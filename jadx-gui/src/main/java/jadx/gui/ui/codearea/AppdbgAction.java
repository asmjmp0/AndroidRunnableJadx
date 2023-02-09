package jadx.gui.ui.codearea;

import jadx.gui.treemodel.JMethod;
import jadx.gui.treemodel.JNode;
import jadx.gui.ui.MainWindow;
import jmp0.apk.ApkFile;
import jmp0.apk.config.DefaultApkConfig;
import jmp0.app.AndroidEnvironment;
import jmp0.app.classloader.ClassLoadedCallbackBase;
import jmp0.app.classloader.XAndroidClassLoader;
import jmp0.app.interceptor.intf.IInterceptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.KeyEvent;
import java.io.File;
import java.nio.file.Path;
import java.util.List;

import static javax.swing.KeyStroke.getKeyStroke;

/**
 * @author jmp0 <jmp0@qq.com>
 * Create on 2023/2/8
 */
public class AppdbgAction extends JNodeAction{

	public AppdbgAction(CodeArea codeArea) {
		super("run with appdbg", codeArea);
		addKeyBinding(getKeyStroke(KeyEvent.VK_R, 0), "trigger appdbg runner");
	}

	@Override
	public void runAction(JNode node) {
		MainWindow mw = getCodeArea().getMainWindow();
		List<Path> paths = mw.getProject().getFilePaths();
		String path = paths.get(0).toFile().getAbsolutePath();
		if (paths.size()!=1) {
			showDialog("only support if only one apk in project");
			return;
		}
		if (!path.endsWith(".apk")){
			showDialog("only support if the type of file is apk");
			return;
		}
		try {
			AppdbgWindow window = new AppdbgWindow(mw,path);
			window.open();
		}catch (Exception e){
			e.printStackTrace();
		}

	}

	@Override
	public boolean isActionEnabled(JNode node) {
		return node instanceof JMethod;
	}

	private void showDialog(String str){
		JPanel contentPanel = new JPanel();
		contentPanel.add(new JLabel(str));
		JFrame frame = new JFrame();
		frame.add(contentPanel);
		frame.setSize(200, 50);
		frame.setLocationRelativeTo(null);
		frame.setVisible(true);
	}
}
