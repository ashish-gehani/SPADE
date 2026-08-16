/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2021 SRI International
 This program is free software: you can redistribute it and/or
 modify it under the terms of the GNU General Public License as
 published by the Free Software Foundation, either version 3 of the
 License, or (at your option) any later version.
 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 General Public License for more details.
 You should have received a copy of the GNU General Public License
 along with this program. If not, see <http://www.gnu.org/licenses/>.
 --------------------------------------------------------------------------------
*/

#include "AddMetadata.h"

#include "llvm/IR/IRBuilder.h"
#include "llvm/Passes/PassPlugin.h"
#include "llvm/Passes/PassBuilder.h"
#include "llvm/Support/CommandLine.h"

#include <sstream>
#include <fstream>
#include <iostream>
#include <cstdlib>
#include <cstring>
#include <cctype>

using namespace llvm;

#define ARG_INPUT_FILE_PATH "config"
#define ARG_OUTPUT "output"
#define ARG_DEBUG "debug"

#define DEBUG_TYPE "add-metadata"
#define STR_ARG_MAX 256

static cl::opt<std::string> configFilePathOption(ARG_INPUT_FILE_PATH, cl::Required, cl::desc("Input file for the pass"), cl::ValueRequired);
static cl::opt<std::string> configOutputOption(ARG_OUTPUT, cl::init(""), cl::Optional, cl::desc("Output specifier for the pass"), cl::ValueRequired);
static cl::opt<bool> debug(ARG_DEBUG, cl::init(false), cl::Optional, cl::desc("Print added or existing metadata as it is added for sanity check"));

static int outputMode;
static std::ofstream outputFile;
static void writeOutput(long counter, StringRef functionName, std::string *paramKey, char *paramDescription);
static void closeOutput();
static bool openOutput();
static bool loadInputFile(std::string inputFilePath);

void parseCallSiteMetadata(Instruction *instruction,
	void (*metadata_callback)(Instruction *instruction, StringRef *functionName, APInt *callSiteNumber, APInt *parameterIndex, StringRef *description));

static StringRef metadataKey("call-site-metadata");
static StringRef metadataKeyIdentifier("call-site-identifier");
static StringRef metadataKeyDescription("call-site-description");

struct ParamInfo{
	bool useIndex;
	APInt index;
	char name[STR_ARG_MAX];
	char description[STR_ARG_MAX];
};

struct FunctionInfo{
	std::vector<struct ParamInfo> paramInfos;
};

static StringMap<FunctionInfo> functionInfos;

static StringMap<long> descriptionCounter;
static long callSiteCounter;

static struct FunctionInfo* getFunctionInfo(StringRef functionName){
	if(functionInfos.find(functionName) == functionInfos.end()){
		return nullptr;
	}else{
		return &functionInfos[functionName];
	}
}

static struct FunctionInfo* initFunctionInfo(StringRef functionName){
	struct FunctionInfo functionInfo;
	functionInfos[functionName] = functionInfo;
	return &functionInfos[functionName];
}

static long getNextDescriptionCounter(char *description){
	/*
	StringRef str(description);
	if(descriptionCounter.find(str) == descriptionCounter.end()){
		descriptionCounter[str] = -1;
	}
	long currentValue = descriptionCounter[str];
	currentValue++;
	descriptionCounter[str] = currentValue;
	return currentValue;
	*/
	return callSiteCounter++;
}

static int copy_str_arg(char *dst, StringRef* src, StringRef msg){
        const char *tempSrc = src->data();
        int tempI = 0;
        for(; tempI < src->size(); tempI++){
                *dst++ = *tempSrc++;
                if(tempI >= STR_ARG_MAX - 1){
                        errs() << msg << " length  must be smaller than " << STR_ARG_MAX << ".\n";
                        return 0;
                }
        }
        *dst = '\0';
        return 1;
}

static bool loadInputFile(std::string inputFilePath){
	std::ifstream inputFile(inputFilePath);
	if(!inputFile.good()){
		errs() << "Failed to read file '" << inputFilePath << "'\n";
		return false;
	}

	std::string line;

	while(std::getline(inputFile, line)){
		StringRef lineRef(line);
		lineRef = lineRef.ltrim(' ');
		if(lineRef.startswith("#") || lineRef.empty()){
			continue;
		}
		SmallVector<StringRef, 3> tokens;
		lineRef.split(tokens, ',', 3, true);

		if(tokens.size() == 3){
			StringRef functionName;

			functionName = tokens[0].trim();
			if(functionName.empty()){
				errs() << "Skipped line '" << line << "' with empty function name\n";
				continue;
			}
			struct FunctionInfo *functionInfo = getFunctionInfo(functionName);
			if(functionInfo == nullptr){
				functionInfo = initFunctionInfo(functionName);
			}

			struct ParamInfo paramInfo;
			StringRef functionParamName;

			functionParamName = tokens[1].trim();
			if(functionParamName.empty()){
				errs() << "Skipped line '" << line << "' with empty function parameter name/index\n";
				continue;
			}

			if(isdigit(functionParamName.front())){ // it means that we are given an index
				if(functionParamName.getAsInteger(10, paramInfo.index)){
					errs() << "Skipped line '" << line << "' with non-numeric function parameter index\n";
					continue;
				}
				if(paramInfo.index.sle(0)){
					errs() << "Skipped line '" << line << "' with non-positive function parameter index\n";
					continue;
				}
				paramInfo.index -= 1;
				paramInfo.useIndex = true;
			}else{
				if(copy_str_arg(&(paramInfo.name[0]), &functionParamName, StringRef("Function parameter name's")) == 0){
					continue;
				}
				paramInfo.useIndex = false;
			}

			StringRef functionDescription;
			functionDescription = tokens[2].trim();
			if(copy_str_arg(&(paramInfo.description[0]), &functionDescription, StringRef("Function description's")) == 0){
				continue;
			}
			/* // Let me be empty if i want
			if(functionDescription.empty()){
				errs() << "Skipped line '" << line << "' with empty function description\n";
				continue;
			}
			*/

			functionInfo->paramInfos.push_back(paramInfo);
		}else{
			errs() << "Skipped unexpected line: '" << line << "'. Expected format: '<function name>, <parameter name/index> <description>'\n";
		}
	}
	return true;
}

void parseCallSiteMetadata(Instruction *instruction,
	void (*metadata_callback)(Instruction *instruction, StringRef *functionName, APInt *callSiteNumber, APInt *parameterIndex, StringRef *description)){
	if(!instruction){
		return;
	}
	MDNode *callSiteNode = instruction->getMetadata(metadataKey);
	if(!callSiteNode){
		return;
	}
	if(callSiteNode->getNumOperands() == 0){
		return;
	}
	StringRef functionName;
	if(CallBase *callBase = dyn_cast<CallBase>(instruction)){
		Function *function = callBase->getCalledFunction();
		if(function == nullptr || !function->hasName()){
			return;
		}
		functionName = function->getName();
	}
	// Format: call-site-metadata = (<call site>, (<param>, <desc>[, <desc>])[, (<param>, <desc>[, <desc>])])
	MDNode::op_iterator callSiteIterator = callSiteNode->op_begin();
	MDNode::op_iterator callSiteIteratorEnd = callSiteNode->op_end();
	if(MDString *callSiteString = dyn_cast<MDString>(*callSiteIterator)){
		APInt callSite;
		if(callSiteString->getString().getAsInteger(10, callSite)){
			return;
		}
		callSiteIterator++;
		while(callSiteIterator != callSiteIteratorEnd){
			if(MDTuple *paramTuple = dyn_cast<MDTuple>(*callSiteIterator)){
				MDNode::op_iterator paramTupleIterator = paramTuple->op_begin();
				MDNode::op_iterator paramTupleIteratorEnd = paramTuple->op_end();
				if(MDString *paramString = dyn_cast<MDString>(*paramTupleIterator)){
					APInt param;
					if(paramString->getString().getAsInteger(10, param)){
						return;
					}
					paramTupleIterator++;
					while(paramTupleIterator != paramTupleIteratorEnd){
						if(MDString *descString = dyn_cast<MDString>(*paramTupleIterator)){
							StringRef description = descString->getString();
							metadata_callback(instruction, &functionName, &callSite, &param, &description);
						}
						paramTupleIterator++;
					}
				}
			}
			callSiteIterator++;
		}
	}
}

static bool conditionalUpdate(Instruction *current, Module &module){
	bool updated = false;

	if(current == nullptr){
		return updated;
	}

	LLVMContext &llvmContext = module.getContext();

	if(CallBase *callBase = dyn_cast<CallBase>(current)){
		Function *function = callBase->getCalledFunction();
		if(function == nullptr || !function->hasName()){
			return updated;
		}
		StringRef functionName = function->getName();
		FunctionInfo *functionInfo = getFunctionInfo(functionName);
		if(functionInfo == nullptr){
			return updated;
		}

		long counter = getNextDescriptionCounter(nullptr);

		StringMap<std::vector<Metadata *>> paramToDesc;

		for(struct ParamInfo &paramInfo : functionInfo->paramInfos){
			Value *operand = nullptr;
			if(paramInfo.useIndex == true){
				if(paramInfo.index.uge(callBase->arg_size())){
					continue; // Skip
				}
				operand = callBase->getArgOperand(paramInfo.index.getZExtValue());
			}else{
				// Use debug info to get the right operand later
			}
			if(operand == nullptr){
				continue; //skip
			}

			std::string paramKey;

			if(paramInfo.useIndex == true){
				paramKey = std::to_string(paramInfo.index.getZExtValue() + 1);
				// get param name from debug info later. Using index for now.
			}else{
				paramKey = std::string(&paramInfo.name[0]);
			}

			writeOutput(counter, functionName, &paramKey, &paramInfo.description[0]);

			if(paramToDesc.find(paramKey.c_str()) == paramToDesc.end()){
				std::vector<Metadata *> list;
				list.push_back(MDString::get(llvmContext, paramKey));
				paramToDesc[paramKey.c_str()] = list;
			}

			std::vector<Metadata *> *list = &paramToDesc[paramKey.c_str()];
			list->push_back(MDString::get(llvmContext, std::string(&paramInfo.description[0])));

			updated = true;
		}

		std::vector<Metadata *> callSiteTuple;
		callSiteTuple.push_back(MDString::get(llvmContext, std::to_string(counter)));
		for(auto const &entry : paramToDesc){
			std::vector<Metadata *> *list = &paramToDesc[entry.first()];
			MDTuple * paramTuple = MDTuple::get(llvmContext, *list);
			callSiteTuple.push_back(paramTuple);
		}
		MDNode *callSiteNode = MDNode::get(llvmContext, callSiteTuple);
		current->setMetadata(metadataKey, callSiteNode);
	}
	return updated;
}

static bool openOutput(){
	std::string configOutput = configOutputOption == "" ? "" : configOutputOption.getValue().c_str();
	if(configOutput.empty()){
		// None
		outputMode = 0;
	}else if(configOutput == "stdout"){
		outputMode = 1;
	}else{ // file
		outputMode = 2;
		outputFile.open(configOutput);
		if(!outputFile.good()){
			errs() << "Invalid output file\n";
			return false;
		}
	}
	return true;
}

static void writeOutput(long counter, StringRef functionName, std::string *paramKey, char *paramDescription){
	if(outputMode == 0){
	}else if(outputMode == 1){
		outs() << counter << "," << functionName << "," << paramKey->c_str() << "," << paramDescription << "\n";
	}else if(outputMode == 2){
		outputFile << counter << "," << functionName.data() << "," << paramKey->c_str() << "," << paramDescription << "\n";
	}
}

static void closeOutput(){
	if(outputMode == 0){
	}else if(outputMode == 1){
	}else if(outputMode == 2){
		outputFile.close();
	}
}

static void debug_metadata_callback(Instruction *instruction, StringRef *functionName, APInt *callSiteNumber, APInt *parameterIndex, StringRef *description){
	errs() << "[DEBUG::" << DEBUG_TYPE << "] function=" << *functionName << ", callSite=" << *callSiteNumber << ", param=" << *parameterIndex << ", description=" << *description << "\n";
}

static void extractAllMetadata(Module &module,
	void (*metadata_callback_func)(Instruction *instruction, StringRef *functionName, APInt *callSiteNumber, APInt *parameterIndex, StringRef *description)){
	for(Function &function : module){
		for(BasicBlock &basicBlock : function){
			for(Instruction &current : basicBlock){
				parseCallSiteMetadata(&current, metadata_callback_func);
			}
		}
	}
}

bool AddMetadata::runOnModule(Module &module){
	std::string inputFilePath = configFilePathOption == "" ? "" : configFilePathOption.getValue().c_str();
	if(inputFilePath.empty()){
		errs() << "Must specify argument '-" << ARG_INPUT_FILE_PATH << "'\n";
		return false;
	}

	if(!loadInputFile(inputFilePath)){
		return false;
	}

	if(!openOutput()){
		return false;
	}

	bool updated = false;
	LLVMContext &llvmContext = module.getContext();

	for(Function &function : module){
		for(BasicBlock &basicBlock : function){
			for(Instruction &current : basicBlock){
				bool currentUpdate = conditionalUpdate(&current, module);
				updated = updated || currentUpdate;
			}
		}
	}

	closeOutput();

	if(debug == true){
		void (*metadata_callback_func)(Instruction *instruction, StringRef *functionName, APInt *callSiteNumber, APInt *parameterIndex, StringRef *description);
		metadata_callback_func = &debug_metadata_callback;
		extractAllMetadata(module, metadata_callback_func);
	}

	return updated;
}

//////////////////////////////////////////////////////////////////////////////

PreservedAnalyses AddMetadata::run(llvm::Module &M, llvm::ModuleAnalysisManager &){
	bool Changed = runOnModule(M);
	return (Changed ? llvm::PreservedAnalyses::none() : llvm::PreservedAnalyses::all());
}

bool LegacyAddMetadata::runOnModule(llvm::Module &M) {
	bool Changed = Impl.runOnModule(M);
	return Changed;
}

//-----------------------------------------------------------------------------
// New PM Registration
//-----------------------------------------------------------------------------
llvm::PassPluginLibraryInfo getAddMetadataPluginInfo() {
  return {LLVM_PLUGIN_API_VERSION, "add-metadata", LLVM_VERSION_STRING,
          [](PassBuilder &PB) {
            PB.registerPipelineParsingCallback(
                [](StringRef Name, ModulePassManager &MPM,
                   ArrayRef<PassBuilder::PipelineElement>) {
                  if (Name == "add-metadata") {
                    MPM.addPass(AddMetadata());
                    return true;
                  }
                  return false;
                });
          }};
}

extern "C" LLVM_ATTRIBUTE_WEAK ::llvm::PassPluginLibraryInfo
llvmGetPassPluginInfo() {
	return getAddMetadataPluginInfo();
}

//-----------------------------------------------------------------------------
// Legacy PM Registration
//-----------------------------------------------------------------------------
char LegacyAddMetadata::ID = 0;

// Register the pass - required for (among others) opt
static RegisterPass<LegacyAddMetadata> X(/*PassArg=*/"legacy-add-metadata", /*Name=*/"LegacyAddMetadata", /*CFGOnly=*/false, /*is_analysis=*/false);
