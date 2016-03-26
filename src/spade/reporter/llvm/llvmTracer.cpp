/* Written against the LLVM 3.0 release.
 * Based on TraceValues pass of LLVM 1.x
 *
 * Usage:
 * gcc llvmReporterLib.c -c -o llvmReporterLib.o
 * g++ LLVMReporter.cpp -shared -o LLVMReporter.so -I$(LLVM_INCLUDE_DIR) -D__STDC_CONSTANT_MACROS -D__STDC_LIMIT_MACROS
 * (On Ubuntu 12.04 LLVM_INCLUDE_DIR = /usr/lib/llvm-3.0/include)
 *
 * "make" on either Mac or Linux; bug reports to Ian.Mason@SRI.com
 *
 * Here are the current changes (by Ian):
 * 1. Fixed the conflation of FILE* with a 32 bit int, that crashes on 64 bit machines.
 * 2. Solved the "Daemon problem" via adding a "smart close" to the system, then doing
 * some linking magic (Chris Dodd and Bruno helped here).
 * 3. Added some cleaner makefiles.
 */

#include "llvm/IR/Constants.h"
#include "llvm/Pass.h"
#include "llvm/IR/Function.h"
#include "llvm/IR/GlobalVariable.h"
#include "llvm/Support/raw_ostream.h"
#include "llvm/IR/Instructions.h"
#include "llvm/IR/Module.h"
#include "llvm/IR/Type.h"
#include "llvm/ADT/ArrayRef.h"
#include "llvm/IR/LLVMContext.h"
#include "llvm/ADT/Twine.h"
#include "llvm/ADT/StringExtras.h"
#include "llvm/IR/IRBuilder.h"
#include "llvm/Support/CommandLine.h"

#include <sstream>
#include<fstream>
#include<iostream>
#include<string.h>

using namespace llvm;
using namespace std;

namespace {
    Value* GetTid; //the syscall argument for getting a Thread ID is different depending on the operating systems.

    // Returns the appropriate specifier for printf based on the type of variable being printed
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

  
    // Creating a Global variable for string 'str', and returning the pointer to the Global  
    static inline GlobalVariable *getStringRef(Module *M, const std::string &str) {
        Constant *Init = ConstantDataArray::getString(M->getContext(), str);
        Twine twine("trstr");
        GlobalVariable *GV = new GlobalVariable(Init->getType(), true, GlobalVariable::InternalLinkage, Init, twine);
        M->getGlobalList().push_back(GV);
        return GV;
    }


    static void InsertPrintInstruction(
            std::vector<Value*> PrintArgsIn,
            BasicBlock *BB,
            Instruction *InsertBefore,
            std::string Message,
            Function *SPADEThreadIdFunc,
            Function * pidFunction,
	    Function *BufferStrings
            ){

        Module *Mod = BB->getParent()->getParent(); //BasicBlock is a child of Function which is a child of Module
       
        //Insert Call to getpid function
        CallInst* pid = CallInst::Create((Value*) pidFunction, Twine("pid"), &(*InsertBefore)); 
	
        //Insert function to get Thread Identifier
        CallInst* threadHandle = CallInst::Create((Value*) SPADEThreadIdFunc, Twine("ThreadHandle"), &(*InsertBefore)); //gets the fd for the getThreadId SPADE library fn
        threadHandle->setTailCall();

        //Message is the string argument to fprintf. GEP is used for getting the handle to Message.
        GlobalVariable *fmtVal;
        fmtVal = getStringRef(Mod, Message);
        Constant *GEP = ConstantExpr::getGetElementPtr((Constant*) fmtVal, ArrayRef<Constant*>(std::vector<Constant*>(2, Constant::getNullValue(Type::getInt64Ty(Mod->getContext())))), 2); 

        //Arguments for fprintf. socketHandle, Message and Thread ID followed by arguments
        std::vector<Value*> PrintArgs;
        
        PrintArgs.push_back(GEP);
        PrintArgs.push_back(threadHandle);
	
        for (unsigned i = 0; i < PrintArgsIn.size(); i++) {
            PrintArgs.push_back(PrintArgsIn[i]);
        }

	if(BufferStrings != NULL){
	    printf("function was retrieved \n");	
	}
	
	CallInst::Create((Value*) BufferStrings, ArrayRef<Value*>(PrintArgs), Twine(""), &(*InsertBefore)); 		 
    }

    static inline void FunctionEntry(
            Function &F,
            Function *SPADEThreadIdFunc,
            Function * pidFunction,
	    Function *BufferStrings
            ){

	//Get the first instruction of the first Basic Block in the function
        BasicBlock &BB = F.getEntryBlock();
        Instruction *InsertPos = BB.begin();

        std::string printString;
        std::string argName;

        raw_string_ostream strStream(printString);
        //Prints the function name to strStream

	//F.printAsOperand(strStream, true, F.getParent());		
	std::string functionName;     
        functionName = F.getName().str();   
        printString = "%lu E: @" + functionName; //WAS  %d  now is %lu is for Thread ID, E is for Function Entry

        unsigned ArgNo = 0;
        std::vector<Value*> PrintArgs;
        for (Function::arg_iterator iterator = F.arg_begin(), E = F.arg_end(); iterator != E; ++iterator, ++ArgNo) {
            if (iterator) {
                argName = "";
                raw_string_ostream strStream2(argName);

		iterator->printAsOperand(strStream2, true, F.getParent());		
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

                PrintArgs.push_back((Value*) iterator);
                printString = printString + " Arg #" + utostr(ArgNo) + ": " + argName + " =" + getPrintfCodeFor(iterator);
            }
        }

        printString = printString + "\n";
        InsertPrintInstruction(PrintArgs, &BB, InsertPos, printString, SPADEThreadIdFunc, pidFunction,  BufferStrings);
    }

    static inline void FunctionExit(
            BasicBlock *BB,
            Function *SPADEThreadIdFunc,
            Function * pidFunction,
	    Function *BufferStrings
            ) {

        ReturnInst *Ret = (ReturnInst*) (BB->getTerminator());

        std::string printString;
        std::string retName;
        raw_string_ostream strStream(printString);
        raw_string_ostream strStream2(retName);
 
	std::string functionName;		
        printString = "%lu L: @" + BB->getParent()->getName().str(); //WAS %d NOW IS %lu is for Thread ID, L is for Function Leave
	
        std::vector<Value*> PrintArgs;
        if (!BB->getParent()->getReturnType()->isVoidTy()) {

            printString = printString + "  R:  "; //R indicates the return value 
	    Ret->getReturnValue()->printAsOperand(strStream2, true, BB->getParent()->getParent());			   
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
        InsertPrintInstruction(PrintArgs, BB, Ret, printString, SPADEThreadIdFunc, pidFunction, BufferStrings);
    }

   	
   static cl::opt<std::string> ArgumentsFileName("FunctionNames-input", cl::init(""), cl::Hidden, cl::desc("specifies the input file for the functionNames"));	


    class InsertMetadataCode : public FunctionPass {
    protected:
        Function* PrintfFunc;
        Function* SPADESocketFunc;
        Function* SPADEThreadIdFunc;
        Function * pidFunction;
	Function* BufferStrings;
	bool monitorMethods;
 	  	
	std::map<std::string, int> methodsToMonitor;	
	
    public:
        static char ID; // Pass identification, replacement for typeid

        InsertMetadataCode() : FunctionPass(ID) {
        }

        bool doInitialization(Module &M) {
   
	    FunctionType * ft = FunctionType::get(Type::getInt32Ty(M.getContext()), false);
	    pidFunction = Function::Create(ft, GlobalValue::ExternalLinkage, "getpid", &M); 


	    std::string fileName = ArgumentsFileName == "" ? "arguments" : ArgumentsFileName.getValue().c_str();	
	    if (strcmp(fileName.c_str(), "-monitor-all") != 0){
	   	 
		    std::ifstream file(fileName);	
		    cout<<"Invoked Do Initialization"<< fileName << endl;
		    std::string str;	 		

		    while (std::getline(file, str))
		    {	
			methodsToMonitor[str] = 1;
			cout<< " Function name FROM FILE : " << str <<"\n"; 
		    }			 	  	
		    monitorMethods = true;
	    }
	    else{
		monitorMethods = false;
	    }
			   
	 
            // Setting up argument types for fprintf
            Type *CharTy = Type::getInt8PtrTy(M.getContext());

            //Ian says FILE* can't be considered a 32 but int on a 64 bit machine.
            //This is for FILE*
            Type *GenericPtr = Type::getInt8PtrTy(M.getContext());

            //64 bit rather than 32?
            //Type *IntTy = Type::getInt32Ty(M.getContext());
            Type *IntTy = Type::getInt64Ty(M.getContext());

            std::vector<Type*> args;
            args.push_back(GenericPtr); //IAM was IntTy
            args.push_back(CharTy);

            //Getting handle for fprintf
            FunctionType *MTy = FunctionType::get(IntTy, ArrayRef<Type*>(args), true);
            PrintfFunc = (Function*) M.getOrInsertFunction("fprintf", MTy);

            //Getting handle for SPADEThreadIdFunc
            //This is used for getting a handle to the OS dependent LLVM_getThreadId() function
            MTy = FunctionType::get(IntTy, false);
            SPADEThreadIdFunc = (Function*) M.getOrInsertFunction("LLVMReporter_getThreadId", MTy);

            //Getting handle for SPADESocketFunc
            //This is used for getting a handle to the socket to SPADE
            MTy = FunctionType::get(GenericPtr, false); //IAM was IntTy
            SPADESocketFunc = (Function*) M.getOrInsertFunction("LLVMReporter_getSocket", MTy);

	    BufferStrings = M.getFunction("bufferString");		
	    if(BufferStrings == NULL){
	        printf(" *** Function not found in module **** \n");		
	    }      
				
            return false;
        }


        bool runOnFunction(Function &F) {
            
	 
	    if(strcmp(F.getName().str().c_str(), "main") == 0){
	       
	       Function * exitingFunction = F.getParent()->getFunction("setAtExit");	 
	       if(exitingFunction == NULL){
			printf("setAtExit was not retrieved \n");
	        }
								      
	        BasicBlock &BB = F.getEntryBlock();
                Instruction *InsertPos = BB.begin();        				
	        CallInst::Create((Value*) exitingFunction, Twine(""), &(*InsertPos));	  
	    }
	
		
	    cout<< "Function name : " << F.getName().str() << "\n";
	    // If the function is not supposed to be monitored just return true
	    if((monitorMethods  &&  methodsToMonitor.find(F.getName().str()) == methodsToMonitor.end()) || strcmp(F.getName().str().c_str(),"bufferString")==0 || strcmp(F.getName().str().c_str(), "flushStrings")==0 || strcmp(F.getName().str().c_str(), "setAtExit")==0 ) {
	        	return true;
            }     
		
            std::vector<Instruction*> valuesStoredInFunction;
            std::vector<BasicBlock*> exitBlocks;

            //FunctionEntry inserts Provenance instrumentation at the start of every function
            FunctionEntry(F, SPADEThreadIdFunc, pidFunction, BufferStrings);
    
            //FunctionExit inserts Provenance instrumentation on the end of every function
            for (Function::iterator BB = F.begin(); BB != F.end(); ++BB) {
                if (isa<ReturnInst > (BB->getTerminator()))
		  FunctionExit(BB, SPADEThreadIdFunc, pidFunction, BufferStrings);
            }
            return true;
        }
    };

    char InsertMetadataCode::ID = 0;
    static RegisterPass<InsertMetadataCode> X("provenance", "insert provenance instrumentation");
	    
}


