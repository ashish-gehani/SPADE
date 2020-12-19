#!/usr/bin/python3

from __future__ import print_function
from bcc import BPF
from bcc import lib
from bcc import _SYM_CB_TYPE
from time import sleep
import sys
from elftools.elf.elffile import ELFFile
import os
import traceback
import inspect
import ctypes
import json
from datetime import datetime
import struct
import argparse
import socket

debug = False

# Size of the max data that can be exported from BPF
maxDataInt = 384

# A map from global generated function id to function instance
functionIdToFunctionMap = {}

# BPF object instance
bpfObject = None

# A map to collect all the arguments of a given function by a process
pidToFunctionIdToArgsList = {}

outputWriterFile = None
outputWriterSocket = None
outputWriterType = None

def cleanupOutput():
	global outputWriterType
	global outputWriterFile
	global outputWriterSocket
	if not outputWriterType is None:
		if outputWriterType == "file":
			if not outputWriterFile is None:
				outputWriterFile.close()
		elif outputWriterType == "tcp":
			if not outputWriterSocket is None:
				outputWriterSocket.close()

### LOGGER - START
def printError(msg):
	print(msg, file=sys.stderr)

def fatal(msg):
	cleanupOutput()
	printError(msg)
	sys.exit(1)

def info(msg):
	print(msg)
### LOGGER - END

### GLOBAL FUNCTION IDS - START
globalFunctionIdCounter = 0
def getNextFunctionId():
	global globalFunctionIdCounter
	globalFunctionIdCounter = globalFunctionIdCounter + 1
	return globalFunctionIdCounter
### GLOBAL FUNCTION IDS - END

### DWARF DATA TYPES - START
# Fixed. If updated then encoding and decoding code must be updated.
# dataTypeId - (dataTypeNameInDwarf, sizeInBytes, isSigned)
dataTypesMap = {
	0	: ("void", 0, False),
	1	: ("char", 1, True),
	2	: ("signed char", 1, True),
	3	: ("unsigned char", 1, False),
	4	: ("short int", 2, True),
	5	: ("short unsigned int", 2, False),
	6	: ("int", 4, True),
	7	: ("unsigned int", 4, False),
	8	: ("long int", 8, True),
	9	: ("long unsigned int", 8, False),
	10	: ("long long int", 8, True),
	11	: ("long long unsigned int", 8, False)
	# The following not supported by BPF
	#12	: ("float", 4, True),
	#13	: ("double", 8, True),
	#14	: ("long double", 16, True)
}
def getTypeIdIntByNameStr(nameStr):
	if nameStr is None or not isinstance(nameStr, (str,)):
		return None
	for dataTypeId,infoTuple in dataTypesMap.items():
		if infoTuple[0] == nameStr:
			return dataTypeId
	return None
def getTypePropertyByIdInt(idInt, indexInt):
	if idInt is None or not isinstance(idInt, (int,)):
		return None
	if idInt in dataTypesMap:
		infoTuple = dataTypesMap[idInt]
		return infoTuple[indexInt]
	return None
def getTypeNameStrByIdInt(idInt):
	return getTypePropertyByIdInt(idInt, 0)
def getTypeSizeIntByIdInt(idInt):
	return getTypePropertyByIdInt(idInt, 1)
def getTypeSignedBoolByIdInt(idInt):
	return getTypePropertyByIdInt(idInt, 2)
### DWARF DATA TYPES - END

### DWARF CLASSES - START
class Type:
	def __init__(self):
		self.IdInt = None
		self.isPointerBool = None
	def setId(self, idInt):
		self.idInt = idInt
	def setIsPointer(self, isPointerBool):
		self.isPointerBool = isPointerBool
	def __str__(self):
		return "type = " + getTypeNameStrByIdInt(self.idInt) + ", isPointer = " + str(self.isPointerBool)

class Parameter:
	def __init__(self):
		self.type = None
		self.nameStr = None
		self.isPointerBool = False
	def setType(self, type):
		self.type = type
	def setName(self, nameStr):
		self.nameStr = nameStr
	def __str__(self):
		return "name = " + self.nameStr + ", " + str(self.type)

class Function:
	def __init__(self):
		self.addrInt = None
		self.nameStr = None
		self.idInt = None
		self.srcFileStr = None
		self.returnType = None
		self.params = []
	def setEntryAddr(self, addrInt):
		self.addrInt = addrInt
	def setName(self, nameStr):
		self.nameStr = nameStr
	def setId(self, idInt):
		self.idInt = idInt
	def setSrcFile(self, srcFileStr):
		self.srcFileStr = srcFileStr
	def setReturnType(self, returnType):
		self.returnType = returnType
	def addParam(self, parameter):
		self.params.append(parameter)
	def __str__(self):
		returnValStr = "srcFile = " + self.srcFileStr + ", name = " + self.nameStr + ", id = " + str(self.idInt) + ", addr = " + str(self.addrInt) + ", return [" + str(self.returnType) + "]"
		paramCount = 0
		for param in self.params:
			returnValStr = returnValStr + ", param_" + str(paramCount) + " [" + str(param) + "]"
			paramCount = paramCount + 1
		return returnValStr
### DWARF CLASSES - END

### DWARF DATA TYPE PARSER - START
def getVoidType(isPointerBool):
	resultType = Type()
	resultType.setId(getTypeIdIntByNameStr("void"))
	resultType.setIsPointer(isPointerBool)
	return resultType

def getTypeFromDIE(startDIE):
	isPointer = False
	while True:
		if startDIE == None:
			return None
		if not "DW_AT_type" in startDIE.attributes:
			return getVoidType(isPointer)
		nextDIE = startDIE.get_DIE_from_attribute("DW_AT_type")
		if nextDIE is None:
			return None
		if nextDIE.tag == "DW_TAG_pointer_type":
			if isPointer == True:
				return getVoidType(isPointer) # skip pointer to a pointer
			isPointer = True
		if "DW_AT_name" in nextDIE.attributes:
			nameAttr = nextDIE.attributes["DW_AT_name"]
			nameStr = str(nameAttr.value.decode())
			typeId = getTypeIdIntByNameStr(nameStr)
			if typeId is None:
				return getVoidType(isPointer) # use void to skip it

			resultType = Type()
			resultType.setId(typeId)
			resultType.setIsPointer(isPointer)
			return resultType
		else:
			startDIE = nextDIE
### DWARF DATA TYPE PARSER - END

### BPF COMMON CODE - START
commonCode = """
#include <uapi/linux/ptrace.h>

#define maxData """ + str(maxDataInt) + """

struct record_t{
	u64 timens;
	u32 pid;
	u16 type;
	u16 functionId;
	u8 data[maxData];
};

BPF_PERF_OUTPUT(records_stream);

static u32 getpid(){ u64 pid_tgid = bpf_get_current_pid_tgid(); u32 pid = pid_tgid >> 32; return pid; }

static void initRecord(struct record_t *record, u16 recordType, u16 functionId){
	record->timens = bpf_ktime_get_ns();
	record->pid = getpid(); record->type = recordType; record->functionId = functionId;
}

static void initEnterRecord(struct record_t *record, u16 functionId){ initRecord(record, 1, functionId); }
static void initReturnRecord(struct record_t *record, u16 functionId){ initRecord(record, 2, functionId); }
static void initArgRecord(struct record_t *record, u16 functionId){ initRecord(record, 3, functionId); }
static void sendRecord(struct pt_regs *ctx, struct record_t *record){ records_stream.perf_submit(ctx, record, sizeof(struct record_t)); }

static void generateRecordFunctionEnter(struct pt_regs *ctx, u16 functionId){
	struct record_t record = {};
	initEnterRecord(&record, functionId);
	sendRecord(ctx, &record);
}

static u8 *copyDataType(void *x, u8 sizeInBytes, u8 *data){
	__builtin_memcpy(data, x, sizeInBytes);
	return data + sizeInBytes;
}

static void copyDataTypeToData(struct record_t *record, int dataOffset, u8 dataType, u8 sizeInBytes, u8 isPtr, unsigned long registerValue){
	record->data[dataOffset] = dataType;
	if(dataType == 0){ // void
	}else if(dataType >= 1 && dataType <= 3){ // treat as str. char, signed char, unsigned char
		__builtin_memset(&(record->data[dataOffset + 1]), 0, (maxData - dataOffset - 1));
		if(isPtr == 0){
			record->data[dataOffset + 1] = registerValue;
		}else{
			bpf_probe_read_kernel_str(&(record->data[dataOffset + 1]), sizeof(u8) * (maxData - dataOffset - 2), (const void *)registerValue);
		}
	}else if(dataType == 4){ // short
		short x; if(isPtr == 0){ x = registerValue; }else{ bpf_probe_read_kernel(&x, sizeInBytes, (const void *)registerValue); }
		copyDataType(&x, sizeInBytes, &(record->data[dataOffset + 1]));
	}else if(dataType == 5){ // unsigned short
		unsigned short x; if(isPtr == 0){ x = registerValue; }else{ bpf_probe_read_kernel(&x, sizeInBytes, (const void *)registerValue); }
		copyDataType(&x, sizeInBytes, &(record->data[dataOffset + 1]));
	}else if(dataType == 6){ // int
		int x; if(isPtr == 0){ x = registerValue; }else{ bpf_probe_read_kernel(&x, sizeInBytes, (const void *)registerValue); }
		copyDataType(&x, sizeInBytes, &(record->data[dataOffset + 1]));
	}else if(dataType == 7){ // unsigned int
		unsigned int x; if(isPtr == 0){ x = registerValue; }else{ bpf_probe_read_kernel(&x, sizeInBytes, (const void *)registerValue); }
		copyDataType(&x, sizeInBytes, &(record->data[dataOffset + 1]));
	}else if(dataType == 8){ // long
		long x; if(isPtr == 0){ x = registerValue; }else{ bpf_probe_read_kernel(&x, sizeInBytes, (const void *)registerValue); }
		copyDataType(&x, sizeInBytes, &(record->data[dataOffset + 1]));
	}else if(dataType == 9){ // unsigned long
		unsigned long x; if(isPtr == 0){ x = registerValue; }else{ bpf_probe_read_kernel(&x, sizeInBytes, (const void *)registerValue); }
		copyDataType(&x, sizeInBytes, &(record->data[dataOffset + 1]));
	}else if(dataType == 10){ // long long
		long long x; if(isPtr == 0){ x = registerValue; }else{ bpf_probe_read_kernel(&x, sizeInBytes, (const void *)registerValue); }
		copyDataType(&x, sizeInBytes, &(record->data[dataOffset + 1]));
	}else if(dataType == 11){ // unsigned long long
		unsigned long long x; if(isPtr == 0){ x = registerValue; }else{ bpf_probe_read_kernel(&x, sizeInBytes, (const void *)registerValue); }
		copyDataType(&x, sizeInBytes, &(record->data[dataOffset + 1]));
	}else if(dataType == 12){ // float
		float x; if(isPtr == 0){ x = registerValue; }else{ bpf_probe_read_kernel(&x, sizeInBytes, (const void *)registerValue); }
		copyDataType(&x, sizeInBytes, &(record->data[dataOffset + 1]));
	}else if(dataType == 13){ // double
		double x; if(isPtr == 0){ x = registerValue; }else{ bpf_probe_read_kernel(&x, sizeInBytes, (const void *)registerValue); }
		copyDataType(&x, sizeInBytes, &(record->data[dataOffset + 1]));
	}else if(dataType == 14){ // long double
		long double x; if(isPtr == 0){ x = registerValue; }else{ bpf_probe_read_kernel(&x, sizeInBytes, (const void *)registerValue); }
		copyDataType(&x, sizeInBytes, &(record->data[dataOffset + 1]));
	}
}

static void generateRecordFunctionReturn(struct pt_regs *ctx, u8 dataType, u8 sizeInBytes, u8 isPtr, u16 functionId){
	int dataOffset;
	struct record_t record = {};
	dataOffset = 0;
	initReturnRecord(&record, functionId);
	copyDataTypeToData(&record, dataOffset, dataType, sizeInBytes, isPtr, ((unsigned long)PT_REGS_RC(ctx)));
	sendRecord(ctx, &record);
}

static void generateRecordFunctionArg(struct pt_regs *ctx, u8 dataType, u8 sizeInBytes, u8 isPtr, u16 functionId, u8 argIndex, unsigned long argRegisterValue){
	int dataOffset;
	struct record_t record = {};
	initArgRecord(&record, functionId);
	record.data[0] = argIndex;
	dataOffset = 1;
	copyDataTypeToData(&record, dataOffset, dataType, sizeInBytes, isPtr, argRegisterValue);
	sendRecord(ctx, &record);
}

"""
### BPF COMMON CODE - END

### BPF CODE GENERATOR - START
def generateFunctionExitHookCode(hookExitFunctionNameStr, function):
	hookCode = "int " + hookExitFunctionNameStr + "(struct pt_regs *ctx){ "
	returnType = function.returnType
	returnTypeId = str(returnType.idInt)
	returnTypeSize = str(getTypeSizeIntByIdInt(returnType.idInt))
	returnTypeIsPtr = str(returnType.isPointerBool == True and 1 or 0)
	hookCode = hookCode + "generateRecordFunctionReturn(ctx, " + returnTypeId + ", " + returnTypeSize + ", " + returnTypeIsPtr + ", " + str(function.idInt) + "); "
	hookCode = hookCode + "return 0; "
	hookCode = hookCode + "}"
	return hookCode

def generateFunctionEntryHookCode(hookEntryFunctionNameStr, function):
	hookCode = "int " + hookEntryFunctionNameStr + "(struct pt_regs *ctx){ "

	paramIndex = 0
	for param in function.params:
		paramType = param.type
		if paramType.idInt == 0: # void
			continue # skip
		paramTypeId = str(paramType.idInt)
		paramTypeSize = str(getTypeSizeIntByIdInt(paramType.idInt))
		paramTypeIsPtr = str(paramType.isPointerBool == True and 1 or 0)
		hookCode = hookCode + "generateRecordFunctionArg(ctx, " + paramTypeId + ", " + paramTypeSize + ", " + paramTypeIsPtr + ", " + str(function.idInt) + ", " + str(paramIndex) + ", ((unsigned long)PT_REGS_PARM" + str(paramIndex + 1) + "(ctx))); "
		paramIndex = paramIndex + 1
		if paramIndex > 5:
			break

	hookCode = hookCode + "generateRecordFunctionEnter(ctx, " + str(function.idInt) + "); "
	hookCode = hookCode + "return 0; "
	hookCode = hookCode + "}"
	return hookCode
### BPF CODE GENERATOR - END

### DWARF DATA TYPE DECODER - START
def parseDataFromBytes(bytesArray, offsetInt):
	dataStr = None
	dataType = bytesArray[offsetInt]
	if dataType == 0:
		dataStr = None
	elif dataType == 1 or dataType == 2 or dataType == 3:
		isSignedBool = getTypeSignedBoolByIdInt(dataType)
		dataChars = []
		for i in range(offsetInt + 1, maxDataInt):
			dataInt = bytesArray[i]
			if not dataInt == 0:
				dataChars.append(chr(dataInt))
		dataStr = "".join(dataChars)
	elif dataType >= 4 and dataType <= 11:
		tempBytesArray = []
		for xxx in bytesArray:
			tempBytesArray.append(bytes([xxx]))
		bytesArray = b''.join(tempBytesArray)
		dataTypeSize = getTypeSizeIntByIdInt(dataType)
		isSignedBool = getTypeSignedBoolByIdInt(dataType)
		bytesSubArray = bytesArray[offsetInt+1:offsetInt+1+dataTypeSize]
		dataInt = int.from_bytes(bytesSubArray, byteorder='little', signed=isSignedBool)
		dataStr = str(dataInt)
	elif dataType == 12 or dataType == 13 or dataType == 14:
		#dataTypeSize = getTypeSizeIntByIdInt(dataType)
		#dataStr = str(struct.unpack("f", bytesArray[offsetInt+1:offsetInt+1+dataTypeSize]))
		pass
	return dataStr
### DWARF DATA TYPE DECODER - START

def outputJSONObject(jsonDict):
	global outputWriterType
	global outputWriterFile
	global outputWriterSocket
	if not jsonDict is None:
		jsonStr = json.dumps(jsonDict) + os.linesep
		if outputWriterType == "file":
			outputWriterFile.write(jsonStr)
			outputWriterFile.flush()
		elif outputWriterType == "tcp":
			outputWriterSocket.sendall(jsonStr.encode())
			pass

### BPF RECORD HANDLERS - START
def updateArgList(record, function): # arg record
	global pidToFunctionIdToArgsList
	pidInt = record.pid
	argIndex = record.data[0]
	param = function.params[argIndex]
	dataOffsetInt = 1
	dataStr = parseDataFromBytes(record.data, dataOffsetInt)
	if not dataStr is None:
		if not pidInt in pidToFunctionIdToArgsList:
			pidToFunctionIdToArgsList[pidInt] = {}
		if not record.functionId in pidToFunctionIdToArgsList[pidInt]:
			pidToFunctionIdToArgsList[pidInt][record.functionId] = []
		pidToFunctionIdToArgsList[pidInt][record.functionId].append({"name":param.nameStr, "value":dataStr, "index":str(argIndex), "type":getTypeNameStrByIdInt(param.type.idInt)})

def removeArgList(record): # enter record
	global pidToFunctionIdToArgsList
	resultList = []
	pidInt = record.pid
	if pidInt in pidToFunctionIdToArgsList:
		if record.functionId in pidToFunctionIdToArgsList[pidInt]:
			resultList = pidToFunctionIdToArgsList[pidInt][record.functionId]
			del pidToFunctionIdToArgsList[pidInt][record.functionId]
		if len(pidToFunctionIdToArgsList[pidInt]) == 0:
			del pidToFunctionIdToArgsList[pidInt]
	return resultList

def getNewJSONDict(function, objectTypeStr, timeNsInt, pidInt, functionId):
	jsonDict = {}
	jsonDict["type"] = objectTypeStr
	jsonDict["nanos"] = str(timeNsInt)
	jsonDict["pid"] = str(pidInt)
	jsonDict["src"] = str(function.srcFileStr)
	jsonDict["function"] = function.nameStr
	return jsonDict

def handleRecords(cpu, data, size):
	global bpfObject
	global functionIdToFunctionMap
	try:
		jsonDict = None
		record = bpfObject["records_stream"].event(data)
		timeNsInt = record.timens
		pidInt = record.pid
		typeInt = record.type
		function = functionIdToFunctionMap[record.functionId]
		nameStr = function.nameStr
		if typeInt == 1:
			argsList = removeArgList(record)
			jsonDict = getNewJSONDict(function, "enter", timeNsInt, pidInt, record.functionId)
			jsonDict["arguments"] = argsList
		elif typeInt == 2:
			dataOffsetInt = 0
			dataStr = parseDataFromBytes(record.data, dataOffsetInt)
			jsonDict = {}
			jsonDict = getNewJSONDict(function, "exit", timeNsInt, pidInt, record.functionId)
			jsonDict["return"] = {"value":dataStr, "type":getTypeNameStrByIdInt(record.data[0])}
		elif typeInt == 3:
			updateArgList(record, function)
		outputJSONObject(jsonDict)
	except Exception as e:
		printError("Failed to process record. Message: " + str(e))
		traceback.print_exc()
### BPF RECORD HANDLERS - END

def pollRecords():
	global bpfObject
	if bpfObject is None:
		return None

	bpfObject["records_stream"].open_perf_buffer(handleRecords)

	info("Polling BPF records")

	while True:
		try:
			bpfObject.perf_buffer_poll()
		except Exception as e:
			printError("Unexpected error in polling the BPF records stream. Message: " + str(e))
			traceback.print_exc()
			break

def generateBPFProbesCode(functionsList):
	global commonCode
	combinedCode = commonCode
	addrToHookEntryNameMap = {}
	addrToHookExitNameMap = {}

	for function in functionsList:
		idStr = str(function.idInt)

		hookEntryFunctionName = "hook_entry_" + idStr
		functionHookEntryCode = generateFunctionEntryHookCode(hookEntryFunctionName, function)

		hookExitFunctionName = "hook_exit_" + idStr
		functionHookExitCode = generateFunctionExitHookCode(hookExitFunctionName, function)

		combinedCode = combinedCode + functionHookEntryCode + os.linesep + functionHookExitCode + os.linesep

		addrToHookEntryNameMap[function.addrInt] = hookEntryFunctionName
		addrToHookExitNameMap[function.addrInt] = hookExitFunctionName

	return combinedCode, addrToHookEntryNameMap, addrToHookExitNameMap

def getFunctionsFromBinary(filePath):
	functionsList = []
	with open(filePath, "rb") as f:
		elfFile = ELFFile(f)

		if not elfFile.has_dwarf_info():
			raise Exception("Missing debug information in binary")

		dwarfInfo = elfFile.get_dwarf_info()

		for CU in dwarfInfo.iter_CUs():
			topDIE = CU.get_top_DIE()
			srcFilePath = str(topDIE.get_full_path())
			for DIE in CU.iter_DIEs():
				if DIE.tag == "DW_TAG_subprogram": # new function
					function = Function()
					function.setSrcFile(srcFilePath)

					if "DW_AT_low_pc" in DIE.attributes:
						function.setEntryAddr(DIE.attributes["DW_AT_low_pc"].value)
					else:
						continue # skip this function

					functionNameAttr = DIE.attributes["DW_AT_name"]
					functionNameStr = str(functionNameAttr.value.decode())

					function.setName(functionNameStr)

					functionReturnType = getTypeFromDIE(DIE)
					if not functionReturnType is None:
						function.setReturnType(functionReturnType)
					else:
						continue # skip this function

					skipFunction = False

					for childDIE in DIE.iter_children():
						if childDIE.tag == "DW_TAG_formal_parameter":

							formalParamNameAttr = childDIE.attributes["DW_AT_name"]
							formalParamNameStr = str(formalParamNameAttr.value.decode())

							parameter = Parameter()
							parameter.setName(formalParamNameStr)

							parameterType = getTypeFromDIE(childDIE)
							if not parameterType is None:
								parameter.setType(parameterType)
								function.addParam(parameter)
							else:
								skipFunction = True

							if skipFunction == True:
								break

					if skipFunction == True:
						continue

					function.setId(getNextFunctionId())
					functionsList.append(function)
	return functionsList

def setupOutput(argOutputPath):
	global outputWriterType
	global outputWriterFile
	global outputWriterSocket
	if argOutputPath.startswith("file://"):
		outputPath = argOutputPath[len("file://"):].strip()
		if outputPath == "":
			fatal("Empty output file path")
		try:
			outputWriterFile = open(outputPath, "w")
		except Exception as e:
			fatal("Failed to open output file path for writing. " + str(e))

		outputWriterType = "file"
	elif argOutputPath.startswith("tcp://"):
		url = argOutputPath[len("tcp://"):].strip()
		tokens = url.split(":")
		if not len(tokens) == 2:
			fatal("Invalid tcp output specifier format. Expected: tcp://<address>:<port>")
		address = tokens[0]
		port = None
		try:
			port = int(tokens[1])
		except Exception as e:
			fatal("Invalid tcp output specifier format. Expected: tcp://<address>:<port>. " + str(e))
		try:
			outputWriterSocket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
			outputWriterSocket.connect((address, port))
		except Exception as e:
			fatal("Failed to connect to tcp socket. " + str(e))
		outputWriterType = "tcp"
	else:
		fatal("Unexpected output URI. Allowed: 'tcp' or 'file'")

def main(args):
	global bpfObject
	global functionIdToFunctionMap

	argFilePath = args.input
	if argFilePath is None or argFilePath.strip() == "":
		fatal("Must specify an input filepath using '-i|--input'")

	argFilePath = os.path.abspath(argFilePath)

	if not os.path.isfile(argFilePath):
		fatal("Must specify a regular file's path using '-i|--input'")

	argOutput = args.output
	if argOutput is None or argOutput.strip() == "":
		fatal("Must specify an output URI using '-o|--output'")

	setupOutput(argOutput)

	info("Arguments [input = " + argFilePath + ", output = " + argOutput + "]")

	functionsList = None
	try:
		functionsList = getFunctionsFromBinary(argFilePath)
	except Exception as e:
		traceback.print_exc()
		fatal("Failed to get functions from binary. " + str(e))

	if functionsList is None or len(functionsList) == 0:
		info("No functions to attach BPF progam to")
		info("Exiting")
	else:
		info("Extracted '" + str(len(functionsList)) + "' functions from binary")

		for function in functionsList:
			functionIdToFunctionMap[function.idInt] = function

		combinedCode, addrToHookEntryNameMap, addrToHookExitNameMap = generateBPFProbesCode(functionsList)

		bpfObject = BPF(text = combinedCode)

		info("BPF program loaded")

		for addrInt, hookEntryNameStr in addrToHookEntryNameMap.items():
			bpfObject.attach_uprobe(name = argFilePath, addr = addrInt, fn_name = hookEntryNameStr)
		for addrInt, hookExitNameStr in addrToHookExitNameMap.items():
			bpfObject.attach_uretprobe(name = argFilePath, addr = addrInt, fn_name = hookExitNameStr)

		info("BPF program attached")

		try:
			pollRecords()
		except:
			pass

		for addrInt, hookEntryNameStr in addrToHookEntryNameMap.items():
			bpfObject.detach_uprobe(name = argFilePath, addr = addrInt)
		for addrInt, hookExitNameStr in addrToHookExitNameMap.items():
			bpfObject.detach_uretprobe(name = argFilePath, addr = addrInt)

		info("BPF program detached")

		info("Exited")

		cleanupOutput();

if __name__ == "__main__":
	parser = argparse.ArgumentParser(description='Binary instrumenter')
	parser.add_argument('-i', '--input', help='Path of the binary (with debug info) to instrument')
	parser.add_argument('-o', '--output', help='Output URI')
	main(parser.parse_args())
