# -*- coding: utf-8 -*-
"""
Spyder Editor

printHisto allows to print histogram of activity of a malware given the 3rd type of files that are output by MLFeatures filter
its pid and a scale for the bins
"""
import pandas as pd
import numpy as np
from datetime import datetime
import matplotlib.pyplot as plt
import matplotlib.dates as mdates

repertoryName = "/Users/mathieubarre/Desktop/TrainSet/"

train = pd.read_csv(repertoryName+"APT29_2017_Fireye_Train_List.csv",sep=',')
Cozy = pd.read_csv(repertoryName+"CozyDuke_8C3E_Train_list.csv",sep=',')

s1 = Cozy.loc[Cozy['Pid']==5292,'UsedDatetime'].values[0].replace('[','').replace(']','')
l1 = s1.split(';')

def transformToMicrosecond(s):
    r = s.split(' ')[1].split('.')[1]
    x = int(r)
    return s.replace(r,str(int((x/10.0))))

r1 = map(str.strip,l1)
r1 = map(transformToMicrosecond,r1)



def toDatetime(s):
    return datetime.strptime(s,'%m/%d/%Y %I:%M:%S.%f %p')

r1 = map(toDatetime,r1)

dateplt1 = mdates.date2num(r1)
print(dateplt1.max() - dateplt1.min())

dateplt1 = (dateplt1 - dateplt1.min())
v = np.arange(dateplt1.min(),dateplt1.max(),step = dateplt1.max()*0.0001)
plt.hist(dateplt1,bins=v)




def printHisto(train1,pid,bins):
    s1 = train1.loc[train1['Pid']==pid,'UsedDatetime'].values[0].replace('[','').replace(']','')
    if(s1 != ''):
        l1 = s1.split(';')
        r1 = map(str.strip,l1)
        r1 = map(transformToMicrosecond,r1)
        r1 = map(toDatetime,r1)
        dateplt1 = mdates.date2num(r1)
        dateplt1 = (dateplt1 - dateplt1.min())
        plt.subplot(211)
        b = np.arange(dateplt1.min(),dateplt1.max(),step = dateplt1.max()*bins)
        plt.hist(dateplt1,bins=b,color='blue',label='used')
        plt.xlabel("time in days")
        plt.ylabel("count of operation")
        plt.legend()
    
    s2 = train1.loc[train1['Pid']==pid,'WgbDatetime'].values[0].replace('[','').replace(']','')
    if(s2 != ''):    
        l2 = s2.split(';')
        r2 = map(str.strip,l2)
        r2 = map(transformToMicrosecond,r2)
        r2 = map(toDatetime,r2)
        dateplt2 = mdates.date2num(r2)
        dateplt2 = (dateplt2 - dateplt2.min())
        plt.subplot(212)
        b = np.arange(dateplt2.min(),dateplt2.max(),step = dateplt2.max()*bins)
        plt.hist(dateplt2,bins=b,color='r',label='wgb')
        plt.xlabel("time in days")
        plt.ylabel("count of operation")
        plt.legend()
        
    
    plt.show()
    
    