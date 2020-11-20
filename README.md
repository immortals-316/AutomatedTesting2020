# AutomatedTesting2020

1. callGraph通过project_To_Analyse文件夹中5个project 所有target里的.class 进行依赖分析  生成.dot 最终生成Report中.pdf
2. findInfectedTestMethods 通过分析callGraph 图，读入changeInfo 输出收到影响的test 的.txt文件