/* @SRI International

   Author: Hashim Sharif
   Email: hsharif3
 
   Description: This file implements an LLVM Pass that creates glibc wrappers around the input bitcode program. The standard libc interface is read from the file "libcAPI"

*/

#include "llvm/Pass.h"
#include "llvm/IR/Function.h"
#include "llvm/IR/Module.h"
#include "llvm/Analysis/CallGraph.h"
#include "llvm/IR/Instruction.h"	
#include "llvm/IR/Instructions.h"
#include "llvm/Support/raw_ostream.h"
#include "llvm/IR/InstIterator.h"
#include "llvm/IR/Constants.h"
#include "llvm/Transforms/Utils/Cloning.h"
#include "llvm/ADT/SmallVector.h"

#include<iostream>
#include<vector>
#include<map>
#include<fstream>

using namespace llvm;
using namespace std;


  struct LibcPass : public ModulePass {

    static char ID;      
    LibcPass() : ModulePass(ID) {}		
    map<string, bool> libcFunctions;
    map<string, bool> undefinedFunctions;
    vector<string> unusedFunctions;	
  

    virtual bool runOnModule(Module & M) {

	
	//Read a standard list of libc functions into the program
        ifstream fin("libcAPI");
	string funcName;

	while(fin >> funcName){
		libcFunctions[funcName] = true; 
	}
		
	
	for (Module::iterator F = M.begin(), Fend = M.end(); F != Fend; ++F) {
      		
	      string functionName = F->getName().str();

	      //If the function name is declared in the application and defined in glibkc
	      if(libcFunctions.find(functionName) != libcFunctions.end() ){
	          
		  StringRef strRef("__wrap_" + functionName); 
		  F->setName(strRef);
		  F->deleteBody();
		  
		  FunctionType * ft = F->getFunctionType();	
		  		 
		  Function* realFunction = Function::Create(ft, GlobalValue::ExternalLinkage, "__real_" + functionName, &M); 	 
		  errs()<<"-Wl,-wrap,"<<functionName<<" ";		
			
		  BasicBlock* entryBlock = BasicBlock::Create(F->getContext(), "entry", F);	 
		  
		  std::vector<Value*> functionArgs;
		
		  for (Function::arg_iterator arg = F->arg_begin(), endArg = F->arg_end(); arg != endArg; ++arg) {
		  	functionArgs.push_back(arg);
		  }

		  CallInst * retVal = CallInst::Create((Value*) realFunction, ArrayRef<Value*>(functionArgs), Twine(""), entryBlock);  
		  if(!ft->getReturnType()->isVoidTy())
		  {
			ReturnInst::Create(F->getContext(), retVal, entryBlock);
		  }
		  else
		  {	
		 	ReturnInst::Create(F->getContext(), entryBlock);
 		  }
		
	     }
	     
	 }
	
	
	 return true;

    };

    
  };


char LibcPass::ID = 0;
static RegisterPass<LibcPass> X("wrapper", "generate libc wrappers", false, false);

