/* Written against the LLVM 3.0 release.
 * Based on TraceValues pass of LLVM 1.x
 * 
 * 
 * Usage:
 * gcc GetLLVMSocket.c -c -o GetLLVMSocket.o
 * g++ LLVMReporter.cpp -shared -o LLVMReporter.so -I$(LLVM_INCLUDE_DIR) -D__STDC_CONSTANT_MACROS -D__STDC_LIMIT_MACROS
 * (On Ubuntu 12.04 LLVM_INCLUDE_DIR = /usr/lib/llvm-3.0/include)
 * 
 * clang -c -emit-llvm foo.c -o foo.bc
 * opt -load LLVMReporter.so -provenance foo.bc -o foo.bc
 * llc foo.bc -o foo.s
 * gcc foo.s GetLLVMSocket.o -o foo
 */

#include "llvm/Constants.h"
#include "llvm/Pass.h"
#include "llvm/Function.h"
#include "llvm/GlobalVariable.h"
#include "llvm/Support/raw_ostream.h"
#include "llvm/Instructions.h"
#include "llvm/Module.h"
#include "llvm/Type.h"
#include "llvm/ADT/ArrayRef.h"
#include "llvm/LLVMContext.h"
#include "llvm/ADT/Twine.h"
#include "llvm/Assembly/Writer.h"
#include "llvm/ADT/StringExtras.h"
#include "llvm/Support/IRBuilder.h"
#include <sys/syscall.h>
#include <sstream>

using namespace llvm;

namespace {
	Value* GetTid; //the syscall argument for getting a Thread ID is different depending on the operating systems.
				
	static std::string getPrintfCodeFor(const Value *V) {
		if (V == 0) return "";
		if (V->getType()->isFloatingPointTy())
			return "%g";
		else if (V->getType()->isLabelTy())
			return "0x%p";
		else if (V->getType()->isPointerTy())
			return "%p";
		else if (V->getType()->isIntegerTy())
			return "%d";
		
		return "%n";
	}
	
	static inline GlobalVariable *getStringRef(Module *M, const std::string &str)
	{
		Constant *Init = ConstantArray::get(M->getContext(), str);
		Twine twine("trstr");
		GlobalVariable *GV = new GlobalVariable(Init->getType(), true, GlobalVariable::InternalLinkage, Init, twine);
		M->getGlobalList().push_back(GV);
		return GV;
	}
	
	static void InsertPrintInstruction(std::vector<Value*> PrintArgsIn, BasicBlock *BB, Instruction *InsertBefore,
                            std::string Message,
                            Function *Printf,Function *SysCall,Function *SPADESocketFunc) {
		Module *Mod = BB->getParent()->getParent(); //BasicBlock is a child of Function which is a child of Module
		//Insert function to get SocketHandle
		CallInst* socketHandle = CallInst::Create((Value*) SPADESocketFunc, Twine("SocketHandle"), &(*InsertBefore)); //gets the fd for the socket to SPADE
		socketHandle->setTailCall();
		
		//Insert function to get Thread ID
		CallInst* tid;
		tid = CallInst::Create((Value*)SysCall, GetTid, Twine("syscall"), &(*InsertBefore));
		tid->setTailCall();
		
		//Message is the string argument to fprintf. GEP is used for getting the handle to Message.
		GlobalVariable *fmtVal;
		fmtVal = getStringRef(Mod, Message);
		Constant *GEP = ConstantExpr::getGetElementPtr((Constant*)fmtVal, ArrayRef<Constant*>(std::vector<Constant*>(2,Constant::getNullValue(Type::getInt64Ty(Mod->getContext())))),2);
		
		//Arguments for fprintf. socketHandle, Message and Thread ID followed by arguments
		std::vector<Value*> PrintArgs;
		PrintArgs.push_back(socketHandle);
		PrintArgs.push_back(GEP);
		PrintArgs.push_back(tid);
		for(unsigned i  = 0 ; i < PrintArgsIn.size(); i++)
		{
			PrintArgs.push_back(PrintArgsIn[i]);
		}
		
		//Inserts call for fprintf
		CallInst::Create((Value*)Printf, ArrayRef<Value*>(PrintArgs), Twine("printMetadata"), &(*InsertBefore));
	}
	
	static inline void FunctionEntry(Function &F, Function *Printf, Function *SysCall,Function *SPADESocketFunc)
	{
		BasicBlock &BB = F.getEntryBlock();
		Instruction *InsertPos = BB.begin();
		
		std::string printString;
		std::string argName;
		
		raw_string_ostream strStream(printString);
		//Prints the function name to strStream
		WriteAsOperand(strStream, &F, false, BB.getParent()->getParent());
		printString = "%d E: " + strStream.str(); //%d is for Thread ID, E is for Function Entry
		
		unsigned ArgNo = 0;
		
		std::vector<Value*> PrintArgs;
		for (Function::arg_iterator iterator = F.arg_begin(), E=F.arg_end(); iterator != E; ++iterator, ++ArgNo){
			if (iterator)
			{
				argName = "";
				raw_string_ostream strStream2(argName);
				WriteAsOperand(strStream2, iterator); // Writes argument name to strStream2
				argName = strStream2.str();
				
				//Escaping % in argName
				std::string Tmp;
				std::swap(Tmp, argName);
				std::string::iterator J = std::find(Tmp.begin(), Tmp.end(), '%');
				//While there are % in Tmp
				while (J != Tmp.end()) {
					argName.append(Tmp.begin(), J);
					argName += "%%";
					++J; // Skip the % at the current location
					Tmp.erase(Tmp.begin(), J);
					J = std::find(Tmp.begin(), Tmp.end(), '%');
				}
				argName += Tmp;
				
				PrintArgs.push_back((Value*)iterator);
				printString = printString + " Arg #" + utostr(ArgNo) + ": " + argName +" =" + getPrintfCodeFor(iterator);
			}
		}
		printString = printString + "\n";
		InsertPrintInstruction(PrintArgs, &BB, InsertPos, printString, Printf, SysCall, SPADESocketFunc);
	}
	
	static inline void FunctionExit(BasicBlock *BB, Function *Printf, Function *SysCall, Function *SPADESocketFunc)
	{
		ReturnInst *Ret = (ReturnInst*)(BB->getTerminator());
		
		std::string printString;
		std::string retName;
		raw_string_ostream strStream(printString);
		raw_string_ostream strStream2(retName);
		
		//Prints the function name to strStream
		WriteAsOperand(strStream, BB->getParent(), false, BB->getParent()->getParent()); //BasicBlock is a child of Function which is a child of Module
		
		printString = "%d L: " + strStream.str(); //%d is for Thread ID, L is for Function Leave
		
		std::vector<Value*> PrintArgs;
		if(!BB->getParent()->getReturnType()->isVoidTy())
		{
			printString = printString + "  R:  "; //R indicates the return value
			WriteAsOperand(strStream2, Ret->getReturnValue()); // Prints the return type and name to strStream2
			retName = strStream2.str();
			
			//Escaping % in retName
			std::string Tmp;
			std::swap(Tmp, retName);
			std::string::iterator I = std::find(Tmp.begin(), Tmp.end(), '%');
			//While there are % in Tmp
			while (I != Tmp.end()) {
				retName.append(Tmp.begin(), I);
				retName += "%%";
				++I; // Skip the % at the current location
				Tmp.erase(Tmp.begin(), I);
				I = std::find(Tmp.begin(), Tmp.end(), '%');
			}
			retName += Tmp;
			
			printString = printString + retName + " =" + getPrintfCodeFor(Ret->getReturnValue());
			
			PrintArgs.push_back(Ret->getReturnValue());
		}
		printString = printString + "\n";
		InsertPrintInstruction(PrintArgs, BB, Ret, printString , Printf, SysCall, SPADESocketFunc);
	}

	class InsertMetadataCode : public FunctionPass
	{
		protected:
			Function* PrintfFunc;
			Function* SysCallFunc;
			Function* SPADESocketFunc;
		public:
			static char ID; // Pass identification, replacement for typeid
			InsertMetadataCode() : FunctionPass(ID) {}
			bool doInitialization(Module &M)
			{
				// Setting up argument types for fprintf and syscall
				Type *CharTy = Type::getInt8PtrTy(M.getContext());
				Type *IntTy = Type::getInt32Ty(M.getContext());
                                Type *GenericPtr = Type::getInt8PtrTy(M.getContext());
				
				std::vector<Type*> args;
				args.push_back(GenericPtr);
				args.push_back(CharTy);
				
				//Getting handle for fprintf
				FunctionType *MTy = FunctionType::get(IntTy,ArrayRef<Type*>(args), true);
				PrintfFunc = (Function*)M.getOrInsertFunction("fprintf", MTy);
				
				//Getting handle for syscall
				MTy = FunctionType::get(IntTy, IntTy, false);
				SysCallFunc = (Function*)M.getOrInsertFunction("syscall", MTy);
				
				//Setting syscall argument for getThreadID
				GetTid = ConstantInt::get(IntTy, SYS_gettid, true);

				//Getting handle for SPADEPipe
				//This is used for getting a handle to the socket to SPADE
				MTy = FunctionType::get(GenericPtr, false);
				SPADESocketFunc = (Function*)M.getOrInsertFunction("GetLLVMSocket", MTy);
				return false;
			}
			
			bool runOnFunction(Function &F)
			{
				std::vector<Instruction*> valuesStoredInFunction;
				std::vector<BasicBlock*> exitBlocks;
				
				//FunctionEntry inserts Provenance instrumentation at the start of every function
				FunctionEntry(F, PrintfFunc, SysCallFunc, SPADESocketFunc);
				
				//FunctionExit inserts Provenance instrumentation on the end of every function
				for (Function::iterator BB = F.begin(); BB != F.end(); ++BB) {
					if (isa<ReturnInst>(BB->getTerminator()))
						FunctionExit(BB, PrintfFunc, SysCallFunc, SPADESocketFunc);
				}
				return true;
			}
	};
	char InsertMetadataCode::ID = 0;
	static RegisterPass<InsertMetadataCode> X("provenance", "insert provenance instrumentation");
}


