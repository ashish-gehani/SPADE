#!/usr/bin/env python2
# -*- coding: utf-8 -*-
"""
Created on Thu Jun 29 13:17:01 2017

@author: mathieubarre
"""

import pandas as pd
import numpy as np
from sklearn.cluster import KMeans,AffinityPropagation,SpectralClustering
from sklearn.ensemble import RandomForestClassifier, RandomForestRegressor,ExtraTreesRegressor,GradientBoostingClassifier
from sklearn.tree import DecisionTreeClassifier
from sklearn.model_selection import cross_val_score,cross_val_predict
from sklearn.linear_model import LogisticRegression
from sklearn.svm import LinearSVC,SVC
from sklearn.metrics import recall_score,precision_score,accuracy_score,precision_recall_curve
import matplotlib.pyplot as plt
from sklearn.feature_extraction.text import CountVectorizer,TfidfVectorizer
import xgboost as xgb
from sklearn.feature_selection import SelectKBest,VarianceThreshold,f_classif,chi2,mutual_info_classif 
from scipy.stats import skew

repertoryName = "/Users/mathieubarre/Desktop/TrainSet/Final/"

Benign1 = pd.read_csv(repertoryName+"BenignLog_Train.csv",sep = ',')
Benign2 = pd.read_csv(repertoryName+"BenignLog1_Train.csv",sep = ',')
Benign3 = pd.read_csv(repertoryName+"BenignLog2_Train.csv",sep = ',')
Benign4 = pd.read_csv(repertoryName+"BenignLog3_Train.csv",sep = ',')
Benign5 = pd.read_csv(repertoryName+"BenignLog4_Train.csv",sep = ',')
Benign6 = pd.read_csv(repertoryName+"BenignLog5_Train.csv",sep = ',')
Benign7 = pd.read_csv(repertoryName+"BenignLog6_Train.csv",sep = ',')
BenignPCM = pd.read_csv(repertoryName+"BenignLogPCMark_Train.csv",sep = ',')
BenignPCM8_1 = pd.read_csv(repertoryName+"BenignPCMark8_Train_1.csv",sep = ',')
BenignPCM8_2 = pd.read_csv(repertoryName+"BenignPCMark8_Train_2.csv",sep = ',')
Sofacy = pd.read_csv(repertoryName+"APT28_2015-12_Kaspersky_Sofacy_AF_Train.csv",sep = ',')
ESET = pd.read_csv(repertoryName+"APT28_2016-10_ESET_Observing_9E77_Train.csv",sep = ',')
ESET1 = pd.read_csv(repertoryName+"APT28_2016-10_ESET_Observing_42DE_Train.csv",sep = ',')
Grizzly = pd.read_csv(repertoryName+"APT29_2016-12_Chris_Grizzly_617BA_Train.csv",sep = ',')
Cozy = pd.read_csv(repertoryName+"CozyDuke_8B35_Train.csv",sep = ',')
Cozy1 = pd.read_csv(repertoryName+"CozyDuke_8C3E_Train.csv",sep = ',')
CozyDropper = pd.read_csv(repertoryName+"CozyDukeDropper_91AA_Train.csv",sep = ',')
Gemini = pd.read_csv(repertoryName+"GeminidukeA365_Train.csv",sep = ',')
Onion = pd.read_csv(repertoryName+"OnionDukeA759_Train.csv",sep = ',')
Sea = pd.read_csv(repertoryName+"SeaDuke3459_Train.csv",sep=',')
Cloud = pd.read_csv(repertoryName+"CloudDuke4800_Train.csv",sep=',')
Hammer = pd.read_csv(repertoryName+"HammerDuke42E6_Train.csv",sep=',')
Mini = pd.read_csv(repertoryName+"Kaspersky_MiniDuke_Train.csv",sep=',')
Cosmic = pd.read_csv(repertoryName+"APT29_2014_FSECURE_Cosmicduke_F621E_Train.csv",sep=',')
Cosmic1 = pd.read_csv(repertoryName+"APT29_2014_FSECURE_Cosmicduke_F621E_b_Train.csv",sep=',')
Cosmic2 = pd.read_csv(repertoryName+"APT29_2014_FSECURE_Cosmicduke_ED14_Train.csv",sep=',')
Bear = pd.read_csv(repertoryName+"APT29_2016-06_Crowdstrike_Bears_CB87_Train.csv",sep=',')
HammerToss = pd.read_csv(repertoryName+"Fireeye_Hammertoss_42e6_Train.csv",sep=',')

l = [Benign1,Benign2,Benign3,Benign4,Benign5,Benign6,Benign7,BenignPCM,BenignPCM8_1,BenignPCM8_2,Sofacy,ESET,Grizzly,Cozy,Cozy1,Sea,Onion,Cloud,CozyDropper,Mini,Cosmic,\
     Cosmic1,Bear,Hammer]


    
GreyListProcess = ["ngen.exe","sihost.exe","svchost.exe","vboxtray.exe","installagent.exe","searchprotocolhost.exe","searchfilterhost.exe","backgroundtaskhost.exe",\
                    "dwm.exe","xblgamesavetask.exe","taskhostw.exe","dllhost.exe","cleanmgr.exe","wermgr.exe","compattelrunner.exe","sc.exe","audiodg.exe","mpcmdrun.exe",\
                    "skypehost.exe","runtimebroker.exe","dmclient.exe","smartscreen.exe","vboxservice.exe","timeout.exe","trustedinstaller.exe","wmiprvse.exe",\
                    "usoclient.exe","lpremove.exe","applicationframeHost.exe","sppsvc.exe","mscorsvw.exe","wsqmcons.exe","ngentask.exe","windump.exe","wuapihost.exe",\
                    "sppextcomobj.exe","sdiagnhost.exe","unpuxlauncher.exe","defrag.exe","smss.exe","tiworker.exe","dashost.exe","vssvc.exe","disksnapshot.exe",\
                    "wininit.exe","csrss.exe","dstokenclean.exe","explorer.exe","provtool.exe","lxrun.exe","microsoftedgecp.exe","helper.exe","iexplore.exe"]

triggeredSuspect = ["conhost.exe","cmd.exe","taskkill.exe","rundll.exe"]

def authorityUser(s):
    return s.rfind("authority") > -1

def low(s):
    return s.lower()

def isInGreyList(x):
    return x in GreyListProcess


def cmdDiff(s):
    return s.rfind("cmd /d /c") > -1

def appearsInString(s):
    res = 0
    for a in triggeredSuspect:
        res += s.rfind(a)
    return res > -len(triggeredSuspect)

def SuspectTriggered(df):
    return df['NameTriggered'].rfind(df['name']) > -1

def addString(l,s):
    res = []
    for a in l :
        res.append(a+s)
    return res
        

voca1={'system':0,'edge':1,'microsoft':2,'hkcu':3,'appdata':4,'app':5,'software':6,'control':7,'run':8,'internet':9,'settings':10,\
     'config':11,'sam':12,'roaming':13,'temp':14,'tmp':15,'network':16,'cryptography':17,'lsa':18,'crypto':19,'policy':20}

# preprocessing function that creates new features based on info on the csv file outut by MLFeatures
# Add the filename features
def preprocess(train):
    train.loc[train['listOfWgbFiles'].isnull(),'listOfWgbFiles'] = ''
    train.loc[train['listOfUsedFiles'].isnull(),'listOfUsedFiles'] = ''
    train.dropna(inplace=True)
    train.drop(train.loc[train['lifeDuration']==0].index,axis=0,inplace=True)
    trainbis = train.drop(["label","commandline","name","User","pid"\
                           ,"avgDurationUsed","avgDurationWgb","state","listOfUsedFiles","listOfWgbFiles","ppid","NameTriggered","writeThenExecutes","countThread"],axis = 1)
    trainbis['avgDurationBetweenTwoUsed'] = trainbis['avgDurationBetweenTwoUsed'].astype(float)
    trainbis['avgDurationBetweenTwoWgb'] = trainbis['avgDurationBetweenTwoWgb'].astype(float)
    trainbis.loc[trainbis['avgDurationBetweenTwoUsed']==max(trainbis['avgDurationBetweenTwoUsed']),'avgDurationBetweenTwoUsed'] = 0.0
    trainbis.loc[trainbis['avgDurationBetweenTwoWgb']==max(trainbis['avgDurationBetweenTwoWgb']),'avgDurationBetweenTwoWgb'] = 0.0
    trainbis[["totalLengthRead","countWgb","countExeDatDllBinWgb","countOfWgbFiles","countExtensionTypeWgb","countNetworkReceive","countOftDirectoriesWgb",\
             "countOfUsedFiles","countFilesystemWgb","countRegSetInfoKey","totalLengthWritten","countFilesystemUsed","countExeDatDllBinUsed",\
             "countNetworkSend","countRegistryWgb","countOfDirectoriesUsed","countUsed","countExtensionTypeUsed","countRegistryUsed","countRegSetValue"]] =\
             trainbis[["totalLengthRead","countWgb","countExeDatDllBinWgb","countOfWgbFiles","countExtensionTypeWgb","countNetworkReceive","countOftDirectoriesWgb",\
             "countOfUsedFiles","countFilesystemWgb","countRegSetInfoKey","totalLengthWritten","countFilesystemUsed","countExeDatDllBinUsed",\
             "countNetworkSend","countRegistryWgb","countOfDirectoriesUsed","countUsed","countExtensionTypeUsed","countRegistryUsed","countRegSetValue"]]\
                       .apply(lambda x : x/(trainbis["lifeDuration"]/10000000))
    trainbis['lifeDuration']= np.log(trainbis['lifeDuration'])
    
    #trainbis = (trainbis - trainbis.mean())
    #trainbis = trainbis/(trainbis.max()-trainbis.min())
    #trainbis["name"]= train["name"].apply(low)
    trainbis['user']= train['User'].apply(low).apply(authorityUser)*1
    trainbis['writeThenExecutes'] = train['writeThenExecutes']
    trainbis["nameInGreyList"] = train['name'].apply(low).apply(isInGreyList)*1
    trainbis["triggersSuspect"] = train['NameTriggered'].apply(low).apply(appearsInString)*1
    trainbis["launchSameNameProcess"] = train.apply(SuspectTriggered,axis=1)
    trainbis["cmdDistinct"] = (train['commandline'].apply(cmdDiff))*(train['name']=='cmd.exe')*1
    trainbis.index = np.arange(0,trainbis.shape[0])
    vectorizerUsed = CountVectorizer(vocabulary=voca1)
    vectorizerWgb = CountVectorizer(vocabulary=voca1)
    X = vectorizerUsed.fit_transform(train['listOfUsedFiles'])
    nameUsed = addString(vectorizerUsed.get_feature_names(),"Used")
    Y = vectorizerWgb.fit_transform(train['listOfWgbFiles'])
    nameWgb = addString(vectorizerWgb.get_feature_names(),"Wgb")
    X = pd.DataFrame(data=X.toarray(),columns=nameUsed)
    #X = X.apply(lambda x : x/(trainbis['countOfUsedFiles']+1))
    Y =  pd.DataFrame(data=Y.toarray(),columns=nameWgb)
    #Y= Y.apply(lambda x : x/(trainbis['countOfWgbFiles']+1))
    trainbis=pd.concat([trainbis,X,Y],axis=1)
    trainbis=trainbis.reindex_axis(sorted(trainbis.columns), axis=1)
    return trainbis


def concatedBis(listData):
    res = []
    for d in listData:
        res.append(preprocess(d))
    return res
    





concatedbis = pd.concat(concatedBis(l))
concated = pd.concat(l)


#skewed_feats = concatedbis.apply(lambda x: skew(x.dropna())) #compute skewness
#skewed_feats = skewed_feats[skewed_feats > 0.75]
#skewed_feats = skewed_feats.index

#concatedbis[skewed_feats] = np.log1p(concatedbis[skewed_feats]+1)

#concatedbis = pd.get_dummies(concatedbis)

#features = ['avgDurationBetweenTwoUsed','avgDurationBetweenTwoWgb','countExeDatDllBinUsed','countExeDatDllBinWgb','countExtensionTypeUsed','countExtensionTypeWgb','countFilesystemUsed',\
#            'countFilesystemWgb','countNetworkReceive','countNetworkSend','countOfDirectoriesUsed','countOfUsedFiles','countOfWgbFiles','countOftDirectoriesWgb','countRegistryUsed',\
#            'countRegistryWgb','countUsed','countWasTriggeredBy','countWgb','lifeDuration','totalLengthRead','totalLengthWritten']

#concatedbis = concatedbis[features]

#vectorizerUsed = CountVectorizer(min_df=0.07,max_df=0.98)
#vectorizerWgb = CountVectorizer(min_df=0.07,max_df=0.98)



#vectorizer = CountVectorizer(token_pattern=r"(?u)\b\w+\-\w+\b",stop_words=['exe'])


#concatedbis=pd.get_dummies(concatedbis)

#concatedbis = (concatedbis - concatedbis.mean())/concatedbis.std()

def labelToWeight(s,p):
    res = 1
    if(s == "tainted"):
        res = p
    return res


y = concated['label']
z= concated['state']

#if you want to use the continuous label as weights remove comment from following lines
#z = 1-y
#z[z==0] = np.ones(z[z==0].size)
# weight = z

#if you want to give a constant weight to tainted process :
#weight = z.apply(labelToWeight,args=(0.1,))




concated['label']=np.floor(concated['label'])
y = concated['label']

params = dict()
#if you want to use the weight in your classification
#params["sample_weight"] = np.array(weight)

      
clf = RandomForestClassifier(max_depth = 25,n_estimators=100,class_weight = "balanced",max_features=0.01)
#clf = RandomForestClassifier(max_depth = 12,n_estimators=100,class_weight = "balanced",max_features=0.3)
clf1 = GradientBoostingClassifier(max_depth=3,learning_rate=0.1,n_estimators=100,max_features=0.9)
clf2 = LogisticRegression(C=1,class_weight="balanced")
clf3 = LinearSVC(C=100,class_weight="balanced")
clf4 = DecisionTreeClassifier(max_depth=15,max_features=0.85,class_weight="balanced")
#clf5 = SVC(C=0.1,class_weight="balanced")
#clf5 = xgb.XGBClassifier(max_depth=3,learning_rate=0.08,n_estimators=250,nthread=8,colsample_bytree=0.3)
clf5 = xgb.XGBClassifier(max_depth=3,learning_rate=0.1,n_estimators=800,nthread=8,colsample_bytree=0.2)

res = cross_val_predict(clf,X=concatedbis,y=y,cv=5,fit_params=params)
res1 = cross_val_predict(clf1,X=concatedbis,y=y,cv=5,fit_params=params)
res2 = cross_val_predict(clf2,X=concatedbis,y=y,cv=5,fit_params=params)
res3 = cross_val_predict(clf3,X=concatedbis,y=y,cv=5,fit_params=params)
res4 = cross_val_predict(clf4,X=concatedbis,y=y,cv=5,fit_params=params)
res5 = cross_val_predict(clf5,X=concatedbis,y=y,cv=5,fit_params=params)
print((cross_val_score(clf,X=concatedbis,y=y,scoring='recall',cv=5,fit_params=params)))

print "######## Logistic Regression #######"
print "recall :" , recall_score(y,res2)
print "precision :" , precision_score(y,res2)
print "accuracy :" , accuracy_score(y,res2)
print "######## Linear SVM #######"
print "recall :" , recall_score(y,res3)
print "precision :" , precision_score(y,res3)
print "accuracy :" , accuracy_score(y,res3)
print "######## XGB #######"
print "recall :" , recall_score(y,res5)
print "precision :" , precision_score(y,res5)
print "accuracy :" , accuracy_score(y,res5)
print "######## Decision Tree #######"
print "recall :" , recall_score(y,res4)
print "precision :" , precision_score(y,res4)
print "accuracy :" , accuracy_score(y,res4)
print "######## Random Forest #######"
print "recall :" , recall_score(y,res)
print "precision :" , precision_score(y,res)
print "accuracy :" , accuracy_score(y,res)
print "####### Gradient boosting  ########"
print "recall :" , recall_score(y,res1)
print "precision :" , precision_score(y,res1)
print "accuracy :" , accuracy_score(y,res1)
print "####### RF AND GB ########"
print "recall :" , recall_score(y,res5*res)
print "precision :" , precision_score(y,res5*res)
print "accuracy :" , accuracy_score(y,res5*res)
print "####### RF OR GB ########"
print "recall :" , recall_score(y,np.ceil((res1+res)/2))
print "precision :" , precision_score(y,np.ceil((res1+res)/2))
print "accuracy :" , accuracy_score(y,np.ceil((res1+res)/2))

def getResults(clf,cv,X,y):
    a = cross_val_predict(clf,X=X,y=y,cv=cv)
    print "recall :" , recall_score(y,a)
    print "precision :" , precision_score(y,a)
    print "accuracy :" , accuracy_score(y,a)
    
    
# function to get the plot of the precision and recall versus the constant weight you apply to the tainted processes
# p is an array containing the range of weights you want to test
# clf is your classifier object    
def plotWeight(clf,p):
    res1 = np.zeros(len(p))
    res2 = np.zeros(len(p))
    for i,a in enumerate(p) :
        weight = z.apply(labelToWeight,args=(a,))
        params = dict()
        params["sample_weight"] = np.array(weight)
        res1[i] = recall_score(y,cross_val_predict(clf,X=concatedbis,y=y,cv=5,fit_params=params))
        res2[i] = precision_score(y,cross_val_predict(clf,X=concatedbis,y=y,cv=5,fit_params=params))
    plt.plot(p,res1,label='recall')
    plt.plot(p,res2,label = 'precision')
    plt.legend()


#function to plot recall and precision or random forest algorithm for a range c of tree depths. x is a parameter to avoid overfitting 
def plotRF(c,x):
    res1 = np.zeros(len(c))
    res2 = np.zeros(len(c))
    for i,a in enumerate(c) :
        clf = RandomForestClassifier(max_depth = a,n_estimators=100,class_weight = "balanced",max_features=x)
        res1[i] = recall_score(y,cross_val_predict(clf,X=concatedbis,y=y,cv=5))
        res2[i] = precision_score(y,cross_val_predict(clf,X=concatedbis,y=y,cv=5))
    plt.plot(c,res1,label='recall')
    plt.plot(c,res2,label = 'precision')
    plt.xlabel("Trees depth")
    plt.legend()
        

# function to plot a recall vs  precison curve for the classifier clf on the set X with label y
def plotPrecisionRecallCurve(clf,X,y):
    y_pred = cross_val_predict(clf,X,y,cv=5,method='predict_proba')[:,1]

    pr, rc, thr = precision_recall_curve(y,y_pred)

    plt.figure(figsize=(15,10))
    lw = 2
    plt.plot(pr, rc, color='darkorange',
         lw=lw, label='PR curve')
    plt.plot([0, 1], [0, 1], color='navy', lw=lw, linestyle='--')
    plt.xlim([0.0, 1.0])
    plt.ylim([0.0, 1.05])
    plt.xlabel('Precision')
    plt.ylabel('Recall')

    plt.legend(loc="lower right")
    plt.show()


clf5.fit(concatedbis,y)
y_predict = clf5.predict(preprocess(Cosmic2))
y_test = np.floor(Cosmic2['label'])
print("############# Test ############")
print "recall :" , recall_score(y_test,y_predict)
print "precision :" , precision_score(y_test,y_predict)
print "accuracy :" , accuracy_score(y_test,y_predict)

y_predict1 = clf5.predict(preprocess(HammerToss))
y_test1 = np.floor(HammerToss['label'])
print("############# Test ############")
print "recall :" , recall_score(y_test1,y_predict1)
print "precision :" , precision_score(y_test1,y_predict1)
print "accuracy :" , accuracy_score(y_test1,y_predict1)

y_predict2 = clf5.predict(preprocess(ESET1))
y_test2 = np.floor(ESET1['label'])
print("############# Test ############")
print "recall :" , recall_score(y_test2,y_predict2)
print "precision :" , precision_score(y_test2,y_predict2)
print "accuracy :" , accuracy_score(y_test2,y_predict2)

y_predict3 = clf5.predict(preprocess(Gemini))
y_test3 = np.floor(Gemini['label'])
print("############# Test ############")
print "recall :" , recall_score(y_test3,y_predict3)
print "precision :" , precision_score(y_test3,y_predict3)
print "accuracy :" , accuracy_score(y_test3,y_predict3)



#concated['pred'] = res5
#concatCsv = concated.drop(['listOfUsedFiles','listOfWgbFiles'],axis=1)
#concatCsv.to_csv(repertoryName+"ConcatedPred.csv",index=False)






