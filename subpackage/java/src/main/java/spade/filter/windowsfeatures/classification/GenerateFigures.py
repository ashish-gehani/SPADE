#!/usr/bin/env python2
# -*- coding: utf-8 -*-
"""
Created on Mon Jun 26 09:50:39 2017

@author: mathieubarre
different functions to obtain the plot present in the report
Here all the data set used in the functions are pandas dataframes
"""

import pandas as pd
import numpy as np
import matplotlib.pyplot as plt


# function to plot the histogram of the labels (good,tainted,bad) given a pandas dataFrame
def plotHistoState(data):
    (data['state'].value_counts()/data.shape[0])[['tainted','good','bad']].plot(kind='bar')
    
# function to plot histogram of continous labels    
def plotHistoLabels(data,bins):
    data['label'].hist(bins = bins,weights = np.ones(data.shape[0])/data.shape[0])
    plt.xlabel("taint level")
    plt.ylabel("proportion")
    
# function to plot the distribution of the values of the continuous features in the set data    
def plotAllDistrib(data,y):
    #y is the label vector
    X_good = data.values[np.where(y==0)[0]]
    X_bad = data.values[np.where(y==1)[0]]

    n_features = data.shape[1]
    fig, axes = plt.subplots(int(np.ceil(n_features//4)), 4, figsize=(20,30))
    axes = axes.ravel()
    for i in range(n_features):
        axes[i].hist([X_good[:, i], X_bad[:, i]], bins=20, label=['Good', 'Bad'],normed=True);
        axes[i].set_title(data.columns[i])
        axes[i].legend(loc=1)
    plt.show()
    
from sklearn.feature_extraction.text import CountVectorizer

cozy = pd.read_csv("/Users/mathieubarre/Desktop/TrainSet/CozyDukeDropper_91AA_Train_bis1.csv",sep=',')
sof = pd.read_csv("/Users/mathieubarre/Desktop/TrainSet/APT28_2015-12_Kaspersky_Sofacy_AF_Train_bis.csv",sep=',')
eset = pd.read_csv("/Users/mathieubarre/Desktop/TrainSet/APT28_2016-10_ESET_Observing_9E77_Train_bis.csv",sep=',')
cosmic1 = pd.read_csv("/Users/mathieubarre/Desktop/TrainSet/APT29_2014_FSECURE_Cosmicduke_F621E_b_Train_bis.csv",sep=',')
bear = pd.read_csv("/Users/mathieubarre/Desktop/TrainSet/APT29_2016-06_Crowdstrike_Bears_CB87_Train_bis.csv",sep=',')
griz  = pd.read_csv("/Users/mathieubarre/Desktop/TrainSet/APT29_2016-12_Chris_Grizzly_617BA_Train_bis.csv",sep=',')
cloud  = pd.read_csv("/Users/mathieubarre/Desktop/TrainSet/CloudDuke4800_Train_bis.csv",sep=',')
cozy1 = pd.read_csv("/Users/mathieubarre/Desktop/TrainSet/CozyDuke_8B35_Train_Bis.csv",sep=',')
gemini = pd.read_csv("/Users/mathieubarre/Desktop/TrainSet/GeminidukeA365_Train_bis.csv",sep=',')
onion = pd.read_csv("/Users/mathieubarre/Desktop/TrainSet/OnionDukeA759_Train_bis.csv",sep=',') 

m = [cozy,sof,eset,cosmic1,bear,griz,cloud,cozy1,gemini,onion]
D = pd.concat(m)
#need to used the data with the FileName column, 2nd type of csv files output by MLFeatures filter 
# function to plot the histogram of the frequency of appeareance of words
def plotStringHisto(data):
    vectorizerUsed = CountVectorizer()
    X = vectorizerUsed.fit_transform(data['FileName'])
    nameUsed = vectorizerUsed.get_feature_names()
    X = pd.DataFrame(data=X.toarray(),columns=nameUsed)
    Y = X.apply(sum)
    ordering = np.argsort(Y)
    plt.figure(figsize=(15,10))
    (Y[ordering][::-1][:30]/X.shape[0]).plot(kind='bar',fontsize=15)
    plt.show()
    
# function to plot the function importance diagram for the classifier clf using the set X with labels y 
def Importance(clf,X,y):
    clf.fit(X,y)
    plt.figure(figsize=(15, 5))
    ordering = np.argsort(clf.feature_importances_)[::-1][:26]


    importances = clf.feature_importances_[ordering]
    feature_names = X.columns[ordering]

    x = np.arange(len(feature_names))
    plt.bar(x, importances)
    plt.xticks(x, feature_names, rotation=90, fontsize=15)
    plt.show()
    
import seaborn as sns

# function to plot the correlation matix of the features of the set X
def heatMap(X):
    corr = X.corr()
    plt.figure(figsize=(15,15))
    sns.heatmap(corr)
    plt.show()