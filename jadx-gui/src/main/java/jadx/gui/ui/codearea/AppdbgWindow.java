package jadx.gui.ui.codearea;

import jadx.api.JavaMethod;
import jadx.core.dex.instructions.args.ArgType;
import jadx.gui.treemodel.JMethod;
import jadx.gui.treemodel.JNode;
import jadx.gui.ui.MainWindow;
import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import jmp0.apk.ApkFile;
import jmp0.apk.config.DefaultApkConfig;
import jmp0.app.AndroidEnvironment;
import jmp0.app.classloader.ClassLoadedCallbackBase;
import jmp0.app.classloader.XAndroidClassLoader;
import jmp0.app.interceptor.unidbg.UnidbgInterceptor;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.RandomStringGenerator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.util.LinkedList;
import java.util.List;


/**
 * @author jmp0 <jmp0@qq.com>
 * Create on 2023/2/9
 */
public class AppdbgWindow extends JFrame {
	private final MainWindow mainWindow;
	private final String path;

	private AndroidEnvironment ae;

	private final JNode node;

	private JTextArea outputTextArea;
	private JTextArea inputTextArea;

	AppdbgWindow(MainWindow mainWindow, String path, JNode node){
		this.mainWindow = mainWindow;
		this.path = path;
		this.node = node;
	}

	private AndroidEnvironment performApk(String path){
		return new AndroidEnvironment(new ApkFile(new File(path), new DefaultApkConfig()), new UnidbgInterceptor(true) {

			@NotNull
			@Override
			public ImplStatus otherNativeCalled(@NotNull String s, @NotNull String s1, @NotNull String s2, @NotNull String s3, @NotNull Object[] objects) {
				return new ImplStatus(false,true);
			}

			@Override
			public Object methodCalled(@NotNull String s, @NotNull String s1, @NotNull String s2, @NotNull String s3, @NotNull Object[] objects) {
				return new ImplStatus(false, null);
			}

			@NotNull
			@Override
			public ImplStatus ioResolver(@NotNull String s) {
				return new ImplStatus(false, null);
			}
		}, new ClassLoadedCallbackBase() {
			@Nullable
			@Override
			public Class<?> beforeResolveClassImpl(@NotNull AndroidEnvironment androidEnvironment, @NotNull String s, @NotNull XAndroidClassLoader xAndroidClassLoader) {
				return null;
			}
		});
	}

	public void open(){
		initUI();
		redirectOE();
		checkDeobfuscationOn();
		setDefaultText();
	}

	private void checkDeobfuscationOn(){
		AppdbgWindow window = this;
		boolean deobfuscationOn = this.mainWindow.getSettings().isDeobfuscationOn();
		if (deobfuscationOn){
			showDialog("appdbg runner don't support deobfuscation,\nclick yes button to disable deobfuscation and reopen.",
					new Runnable() {
						@Override
						public void run() {
							mainWindow.getSettings().setDeobfuscationOn(false);
							mainWindow.reopen();
							window.dispose();
						}
					}, null);
		}
	}

	private String getArgTypeString(ArgType argType){
		if (argType.isObject()){
			return argType.getObject();
		}else if (argType.isPrimitive()){
			return argType.getPrimitiveType().getLongName();
		}else if (argType.isArray()){
			return this.getArgTypeString(argType.getArrayElement()) + "[]";
		}
		else return "";
	}

	private String getObjectInitString(ArgType argType){
		if (argType.isObject()){
			switch (argType.getObject()){
				case "java.lang.String":
					return "new String(\"\")";

			}
			return "/**fill by your self**/";
		}else if (argType.isPrimitive()){
			switch (argType.getPrimitiveType().getLongName()){
				case "int":
					return "new Integer(0)";
				case "short":
					return "new Short((short) 0)";
				case "long":
					return "new Long(0L)";
				case "float":
					return "new Float(0.0f)";
				case "double":
					return "new Double(0.0d)";
				case "byte":
					return "new Byte((byte) 0)";
				case "boolean":
					return "new Boolean(false)";
				case "char":
					return "new Character(' ')";
			}
		}else if (argType.isArray()){
			return "new " + this.getArgTypeString(argType.getArrayElement()) +"[]{}";
		}
		return "/**fill by your self**/";
	}

	private String generateBody(String className, String methodName, List<ArgType> argTypeList,ArgType returnType,boolean isStatic){
		//setAccessible
		StringBuilder stringBuilder = new StringBuilder();
		stringBuilder.append(String.format("Class cz = ae.findClass(\"%s\");\n",className))
				.append("//if the type is defined in apk, you should use 'ae.findClass()' to get\n");
		String clzsFmt;
		String objFmt;
		if (argTypeList.size() == 0){
			clzsFmt = String.format("Class[] clzs = new Class[]{%s};\n", StringUtils.repeat("%s,",argTypeList.size()));
			objFmt = String.format("Object[] os = new Object[]{%s};\n", StringUtils.repeat("%s,",argTypeList.size()));
		}else {
			String repeat = StringUtils.repeat("%s,",argTypeList.size());
			repeat = repeat.substring(0,repeat.length() - 1);
			clzsFmt = String.format("Class[] clzs = new Class[]{%s};\n", repeat);
			objFmt = String.format("Object[] os = new Object[]{%s};\n", repeat);
		}

		List<String> clzStrs = new LinkedList<>();
		List<String> objsStrs = new LinkedList<>();
		for (ArgType argType:argTypeList){
			clzStrs.add(this.getArgTypeString(argType)+".class");
			objsStrs.add(this.getObjectInitString(argType));
		}
		stringBuilder.append(String.format(clzsFmt,clzStrs.toArray()));
		if (!isStatic){
			stringBuilder.append("//you should initialize the class by yourself!\n");
		}
		stringBuilder.append("Object ins = null;\n")
						.append(String.format("java.lang.reflect.Method m = cz.getDeclaredMethod(\"%s\",clzs);\n",methodName))
						.append("m.setAccessible(true);\n");
		stringBuilder.append("// you need to fill the array\n\n")
				.append(String.format(objFmt,objsStrs.toArray()));
		String returnTypeName =  this.getArgTypeString(returnType);
		String typeString = this.getArgTypeString(returnType);
		if (typeString.equals("int")){
			typeString = "Integer";
		} else typeString = typeString.substring(0, 1).toUpperCase() + typeString.substring(1);
		if (returnTypeName.equals("void")){
			stringBuilder.append("m.invoke(ins,os);\n")
					.append("return \"void return type,nothing returned.\";\n");
		}else {
			if (returnType.isPrimitive()){
				stringBuilder.append(String.format("%s result = (%s) m.invoke(ins,os);\n",typeString, typeString))
						.append("return result.toString();\n");
			}else {
				stringBuilder.append("Object result = m.invoke(ins,os);\n")
						.append("return result.toString();\n");
			}

		}
		return stringBuilder.toString();
	}

	private void setDefaultText(){
		StringBuilder builder = new StringBuilder();
		builder.append("public static String run(jmp0.app.AndroidEnvironment ae){\n");

		if (this.node instanceof JMethod){
			JavaMethod javaMethod = ((JMethod) this.node).getJavaMethod();
			String className = javaMethod.getDeclaringClass().getFullName();
			String methodName = javaMethod.getName();
			boolean aStatic = javaMethod.getAccessFlags().isStatic();
			List<ArgType> argTypeList = javaMethod.getArguments();
			ArgType returnType = javaMethod.getReturnType();
			builder.append(this.generateBody(className,methodName,argTypeList,returnType,aStatic));
		}else {
			builder.append("return \"appdbg\";\n");
		}
		builder.append("}\n");
		inputTextArea.setText(builder.toString());
	}

	private void doWork(){
		if (ae == null){
			System.out.println("AndroidEnvironment not initialized yet,please click init button first!");
			return;
		}
		RandomStringGenerator builder = new RandomStringGenerator.Builder().build();
		String className = "jmp0.jadx.call.CallClass"+builder.generate(4);
		try {
			byte[] bs = this.makeRandomClassWithMethod(className,inputTextArea.getText());
			ae.getClassLoader().xDefineClass(null,bs,0,bs.length);
			String ret = (String) ae.findClass(className).getDeclaredMethod("run",AndroidEnvironment.class).invoke(null,ae);
			this.outputTextArea.append("result=>"+ ret + '\n');
		}catch (Exception e){
			e.printStackTrace();
		}
	}

	private void onClose(){
		System.setOut(System.out);
		System.setErr(System.err);
	}

	private void redirectOE(){
		OutputStream os = new OutputStream() {
			@Override
			public void write(int b){
				outputTextArea.append(""+(char)(b));
			}
		};

		PrintStream ps = new PrintStream(os);
		System.setOut(ps);
		System.setErr(ps);
	}

	private byte[] makeRandomClassWithMethod(String className,String methodContent) throws CannotCompileException, IOException {
		CtClass ctClass = ClassPool.getDefault().makeClass(className);
		ctClass.addMethod(CtMethod.make(methodContent,ctClass));
		return ctClass.toBytecode();
	}

	private void showDialog(String content,Runnable rightRun,Runnable falseRun){
		JFrame jFrame = new JFrame();
		JTextArea textArea = new JTextArea(content,3,6);
		textArea.setLineWrap(true);
		textArea.setEditable(false);
		JButton rightButton = new JButton("yes");
		JButton falseButton = new JButton("no");
		rightButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (rightRun!=null)
					rightRun.run();
				jFrame.dispose();
			}
		});

		falseButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (falseRun!=null)
					falseRun.run();
				jFrame.dispose();
			}
		});
		JPanel buttonPanel = new JPanel();
		buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.X_AXIS));
		buttonPanel.add(rightButton);
		buttonPanel.add(falseButton);
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.add(textArea);
		panel.add(buttonPanel);
		jFrame.add(panel);
		jFrame.setSize(400, 100);
		jFrame.setLocationRelativeTo(null);
		jFrame.setVisible(true);

	}


	private void initUI(){
		setTitle("Appdbg Runner");
		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				// Perform custom actions when the frame is closed
				onClose();
			}
		});
		inputTextArea = new JTextArea();
		inputTextArea.setSize(new Dimension(800,250));
		inputTextArea.setLineWrap(true);
		JButton button = new JButton("run");
		JButton initBtn = new JButton("init");
		JPanel jPanel = new JPanel();
		jPanel.setLayout(new BoxLayout(jPanel, BoxLayout.X_AXIS));
		jPanel.add(initBtn);
		jPanel.add(button);

		this.outputTextArea = new JTextArea();
		outputTextArea.setSize(new Dimension(800,250));

		button.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				doWork();
			}
		});

		initBtn.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				System.out.println("initialize AndroidEnvironment now! please wait for a while.");
				new Thread(new Runnable() {
					@Override
					public void run() {
						try {
							ae = performApk(path);
							System.out.println("initialize completed! AndroidEnvironment instance:" + ae);
						}catch (SecurityException securityException){
							System.out.println("initialize failed, please use the jdk from appdbg-JDK,and clean the temp dir in jadx installed dir.");
							securityException.printStackTrace();
						}catch (Throwable throwable){
							throwable.printStackTrace();
						}
					}
				}).start();
			}
		});

		JScrollPane inputScrollPane = new JScrollPane(inputTextArea,ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS,ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS);
		JScrollPane outputScrollPane = new JScrollPane(outputTextArea,ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS,ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS);
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.add(inputScrollPane);
		panel.add(jPanel);
		panel.add(outputScrollPane);
		add(panel);
		setPreferredSize(new Dimension(1000,500));
		setLocationRelativeTo(null);
		pack();
		setVisible(true);
	}

}
