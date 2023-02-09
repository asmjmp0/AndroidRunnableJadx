package jadx.gui.ui.codearea;

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
import jmp0.app.interceptor.intf.IInterceptor;
import jmp0.app.interceptor.unidbg.UnidbgInterceptor;
import org.apache.commons.text.RandomStringGenerator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;


/**
 * @author jmp0 <jmp0@qq.com>
 * Create on 2023/2/9
 */
public class AppdbgWindow extends JFrame {
	private final MainWindow mainWindow;
	private final String path;

	private JTextArea outputTextArea;
	private JTextArea inputTextArea;

	AppdbgWindow(MainWindow mainWindow,String path){
		this.mainWindow = mainWindow;
		this.path = path;
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
		inputTextArea.setText("public static String run(jmp0.app.AndroidEnvironment ae){\n    reutrn \"appdbg\";\n}");
	}

	private void doWork(){
		AndroidEnvironment ae = performApk(this.path);
		RandomStringGenerator builder = new RandomStringGenerator.Builder().build();
		String className = "jmp0.jadx.call.CallClass"+builder.generate(4);
		try {
			byte[] bs = this.makeRandomClassWithMethod(className,inputTextArea.getText());
			ae.getClassLoader().xDefineClass(null,bs,0,bs.length);
			String ret = (String) ae.findClass(className).getDeclaredMethod("run",AndroidEnvironment.class).invoke(null,ae);
			this.outputTextArea.append(ret + '\n');
		}catch (Exception e){
			e.printStackTrace();
		}
	}

	private byte[] makeRandomClassWithMethod(String className,String methodContent) throws CannotCompileException, IOException {
		CtClass ctClass = ClassPool.getDefault().makeClass(className);
		ctClass.addMethod(CtMethod.make(methodContent,ctClass));
		return ctClass.toBytecode();
	};


	private void initUI(){
		inputTextArea = new JTextArea();
		inputTextArea.setSize(new Dimension(800,250));
		inputTextArea.setLineWrap(true);
		JButton button = new JButton("run");
		this.outputTextArea = new JTextArea();
		outputTextArea.setSize(new Dimension(800,250));
		outputTextArea.setEditable(false);

		button.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				doWork();
			}
		});

		JScrollPane inputScrollPane = new JScrollPane(inputTextArea,ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS,ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS);
		JScrollPane outputScrollPane = new JScrollPane(outputTextArea,ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS,ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS);
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.add(inputScrollPane);
		panel.add(button);
		panel.add(outputScrollPane);
		add(panel);
		setSize(new Dimension(1000,500));
		setLocationRelativeTo(null);
		pack();
		setVisible(true);
	}

}
