
import com.ibm.wala.classLoader.Language;
import com.ibm.wala.classLoader.ShrikeBTMethod;
import com.ibm.wala.ipa.callgraph.*;
import com.ibm.wala.ipa.callgraph.impl.AllApplicationEntrypoints;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.callgraph.propagation.SSAPropagationCallGraphBuilder;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.shrikeCT.InvalidClassFileException;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.annotations.Annotation;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.config.AnalysisScopeReader;
import com.ibm.wala.util.io.FileProvider;

import java.io.*;
import java.util.*;


public class MyWala {
    static Map<String, Set<String>> classMap;
    static Map<String, Set<String>> methodMap;
    static Map<String, Set<String>> classMethodMap;
    static Map<String, ShrikeBTMethod> BTMethodMap;
    static String type;
    static String project_target;
    static String change_info_path;

    static Set<String> temps = new HashSet<>();


    public static void main(String[] args) throws IOException, ClassHierarchyException, InvalidClassFileException, CancelException {
//        type = "-m";
//        project_target = "C:\\Users\\lenovo\\Desktop\\study\\大三上\\自动化\\ClassicAutomatedTesting\\ClassicAutomatedTesting\\5-MoreTriangle\\target";
//        change_info_path = "C:\\Users\\lenovo\\Desktop\\study\\大三上\\自动化\\Data\\5-MoreTriangle\\change_info.txt";
        type=args[0];
        project_target=args[1];
        change_info_path=args[2];
        MyWala t=new MyWala();
        try {
            t.initialize();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(0);
        }
        if (type.equals("-c")) {//类选择
            ByClass();
        } else if (type.equals("-m")) {//方法选择
            ByMethod();
        }
    }
    //完成整个构建过程
    public static void initialize() throws IOException, ClassHierarchyException, CancelException, InvalidClassFileException {
        //init scope
        AnalysisScope scope = AnalysisScopeReader.readJavaScope("scope.txt", new File("src/main/resources/exclusion.txt"), MyWala.class.getClassLoader());
        File folder = new File(project_target);
        Vector<File> classes = new Vector<>();
        getClasses(folder, classes);
        for (File f :
                classes) {
            scope.addClassFileToScope(ClassLoaderReference.Application, f);
        }

        classMap = new HashMap<>();
        methodMap = new HashMap<>();
        classMethodMap = new HashMap<>();
        BTMethodMap = new HashMap<>();


        // 1.生成类层次关系对象
        ClassHierarchy cha = ClassHierarchyFactory.makeWithRoot(scope);
        // 2.生成进入点
        AllApplicationEntrypoints entrypoints = new AllApplicationEntrypoints(scope, cha);
        AnalysisOptions option = new AnalysisOptions(scope, entrypoints);
        SSAPropagationCallGraphBuilder builder = Util.makeZeroCFABuilder(
                Language.JAVA, option, new AnalysisCacheImpl(), cha, scope);

        CallGraph cg = builder.makeCallGraph(option);
        // 4.遍历cg中所有的节点
        for (CGNode node : cg) {
            // node中包含了很多信息，包括类加载器、方法信息等，这里只筛选出需要的信息
            if (node.getMethod() instanceof ShrikeBTMethod) {
                //node.getMethod()返回一个比较泛化的IMethod实例，不能获取到我们想要的信息

                ShrikeBTMethod method = (ShrikeBTMethod) node.getMethod();

                if ("Application".equals(method.getDeclaringClass().getClassLoader().toString())) {
                    // 获取声明该方法的类的内部表示
                    String classInnerName = method.getDeclaringClass().getName().toString();
                    classMap.putIfAbsent(classInnerName, new HashSet<>());
                    classMethodMap.putIfAbsent(classInnerName, new HashSet<>());

                    // 获取方法签名
                    String signature = method.getSignature();
                    String class_signature = classInnerName + " " + signature;
                    methodMap.putIfAbsent(class_signature, new HashSet<>());
                    BTMethodMap.putIfAbsent(class_signature, method);
                    classMethodMap.get(classInnerName).add(classInnerName + " " + signature);
                    System.out.println(classInnerName + " " + signature);
                    Iterator<CGNode> preIt = cg.getPredNodes(node);
                    while (preIt.hasNext()) {
                        CGNode preNode = preIt.next();
                        if (preNode.getMethod() instanceof ShrikeBTMethod) {
                            ShrikeBTMethod preMethod = (ShrikeBTMethod) preNode.getMethod();
                            // 使用Primordial类加载器加载的类都属于Java原生类，我们一般不关心。
                            if ("Application".equals(preMethod.getDeclaringClass().getClassLoader().toString())) {

                                // 获取声明该方法的类的内部表示
                                String preClass = preMethod.getDeclaringClass().getName().toString();
                                classMap.get(classInnerName).add(preClass);
                                // 获取方法签名
                                String preSignature = preMethod.getSignature();
                                Collection<Annotation> annotations = preMethod.getAnnotations();
                                methodMap.get(class_signature).add(preClass + " " + preSignature);

                                System.out.println(preClass + " " + preSignature);
                                for (Annotation a : annotations) {
                                    System.out.println(a.getType());
                                }

                            }
                        }
                    }
                }
            } else {
                System.out.println(String.format("'%s'不是一个ShrikeBTMethod：%s", node.getMethod(), node.getMethod().getClass()));
            }
        }
        classMap.entrySet().removeIf(item -> item.getValue().isEmpty());
        methodMap.entrySet().removeIf(item -> item.getValue().isEmpty());
        getDot();

    }

    public static void getClasses(File origin, Vector<File> classes) {//get all .class files in the root file
        if (!origin.isFile()) {
            for (File innerFolder :
                    Objects.requireNonNull(origin.listFiles())) {
                if (innerFolder.isFile() && innerFolder.getName().endsWith(".class")) {
                    classes.add(innerFolder);
                } else {
                    getClasses(innerFolder, classes);
                }
            }
        }


    }

    public static void getDot() {

        try {
            BufferedWriter classOut = new BufferedWriter(new FileWriter("class-DataLog.dot"));
            String classTitle = "digraph cmd_class {\n";
            classOut.write(classTitle);
            for (Map.Entry<String, Set<String>> entry : classMap.entrySet()) {
                for (String str : entry.getValue()) {
                    String classLine = "   \"" + entry.getKey() + "\" -> \"" + str + "\";\n";
                    classOut.write(classLine);
                }
            }
            classOut.write("}");
            classOut.close();
            BufferedWriter methodOut = new BufferedWriter(new FileWriter("method-DataLog.dot"));
            String methodTitle = "digraph cmd_method {\n";
            methodOut.write(methodTitle);
            for (Map.Entry<String, Set<String>> entry : methodMap.entrySet()) {
                String key = entry.getKey().split(" ")[1];
                for (String str : entry.getValue()) {

                    String value = str.split(" ")[1];
                    String methodLine = "   \"" + key + "\" -> \"" + value + "\";\n";
                    methodOut.write(methodLine);
                }
            }
            methodOut.write("}");
            methodOut.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void ByClass() {
        Set<String> classChange = new HashSet<>();
        Set<String> methodChange = new HashSet<>();

        try {
            BufferedReader in = new BufferedReader(new FileReader(change_info_path));
            String str;
            while ((str = in.readLine()) != null) {
                String[] strings = str.split(" ");
                classChange.add(strings[0]);
                methodChange.add(str);
            }
            System.out.println(str);
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            BufferedWriter classSelectionOut = new BufferedWriter(new FileWriter("./selection-class.txt"));
            Set<String> methods = new HashSet<>();
            temps = new HashSet<>();
            for (String className : classChange) {
                for (String classMethod : classMethodMap.get(className)) {
                    Rec(classMethod, methods);
                }
            }
            for (String method : methods) {
                classSelectionOut.write(method + "\n");
            }
            classSelectionOut.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public static void ByMethod() {
        Set<String> methodChange = new HashSet<>();

        try {
            BufferedReader in = new BufferedReader(new FileReader(change_info_path));
            String str;
            while ((str = in.readLine()) != null) {

                methodChange.add(str);
            }
            System.out.println(str);
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {

            BufferedWriter methodSelectionOut = new BufferedWriter(new FileWriter("./selection-method.txt"));
            Set<String> methods = new HashSet<>();
            temps = new HashSet<>();
            for (String methodName : methodChange) {
                Rec(methodName, methods);
            }
            for (String method : methods) {
                methodSelectionOut.write(method + "\n");
            }
            methodSelectionOut.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void Rec(String method, Set<String> methods) {
        if (!methodMap.containsKey(method)) {
            return;
        }
        Set<String> methodNames = methodMap.get(method);

        for (String methodName : methodNames) {

            if (methodMap.containsKey(methodName)) {
                if (!temps.contains(methodName)) {
                    temps.add(methodName);
                    Rec(methodName, methods);
                }
            } else {
                boolean flag = false;
                Collection<Annotation> annotations = BTMethodMap.get(methodName).getAnnotations();
                for (Annotation annotation : annotations) {
                    if (annotation.getType().toString().equals("<Application,Lorg/junit/Test>")) {
                        flag = true;
                        break;
                    }
                }
                if (BTMethodMap.get(methodName).isInit() || BTMethodMap.get(methodName).isClinit()) {
                    flag = false;
                }
                if (flag) {
                    methods.add(methodName);
                }

            }
        }
        return;

    }
}