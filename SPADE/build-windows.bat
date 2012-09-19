@set JAVAC_PATH="C:\Program Files\Java\jdk1.6.0_30\bin\javac"

%JAVAC_PATH% -cp build;lib\* -sourcepath src -d build src\spade\core\*.java
%JAVAC_PATH% -cp build;lib\* -sourcepath src -d build src\spade\client\*.java
%JAVAC_PATH% -cp build;lib\* -sourcepath src -d build src\spade\utility\*.java
%JAVAC_PATH% -cp build;lib\* -sourcepath src -d build src\spade\filter\*.java
%JAVAC_PATH% -cp build;lib\* -sourcepath src -d build src\spade\storage\*.java
%JAVAC_PATH% -cp build;lib\* -sourcepath src -d build src\spade\reporter\ProcMon.java
