/*
Copyright 2009 Hauke Rehfeld


This file is part of QuakeInjector.

QuakeInjector is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

QuakeInjector is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with QuakeInjector.  If not, see <http://www.gnu.org/licenses/>.
*/
package de.haukerehfeld.quakeinjector.gui;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;

import javax.swing.BoxLayout;
import javax.swing.InputVerifier;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import de.haukerehfeld.quakeinjector.ChangeListenerList;
import de.haukerehfeld.quakeinjector.RelativePath;

/**
 * A Panel to input paths
 */
public class JPathPanel extends JPanel {
	private final ArrayList<ErrorListener> errorListeners = new ArrayList<ErrorListener>();
	private final ChangeListenerList changeListeners = new ChangeListenerList();

	private final int inputLength = 32;

	private File basePath;

	private final JTextField path;
	private final JLabel errorLabel;
	private final JButton fileChooserButton;
	private final JFileChooser chooser;


	private final Verifier check;

	/**
	 * If the saved path is absolute although we have a basepath - happens on windows when the basepath is
	 * on a different drive
	 */
	private boolean absolute = false;

	/**
	 * @param filesAndOrDirectories what kind of files can be selected with the filechooser:
	 *        one of JFileChooser.DIRECTORIES_ONLY,
	 *               JFileChooser.FILES_AND_DIRECTORIES,
	 *               JFileChooser.FILES_ONLY
	 */
	public JPathPanel(Verifier check, File defaultPath, int filesAndOrDirectories) {
		this(check, defaultPath, null, filesAndOrDirectories);
	}

	/**
	 * @param filesAndOrDirectories what kind of files can be selected with the filechooser:
	 *        one of JFileChooser.DIRECTORIES_ONLY,
	 *               JFileChooser.FILES_AND_DIRECTORIES,
	 *               JFileChooser.FILES_ONLY
	 */
	public JPathPanel(Verifier check,
					  File defaultPath,
					  File basePath,
					  int filesAndOrDirectories) {
		this.check = check;
		this.basePath = basePath;
		
		setLayout(new BoxLayout(this, BoxLayout.LINE_AXIS));

		absolute = defaultPath.isAbsolute();

		this.path = new JTextField(defaultPath.toString(), inputLength);
		PathVerifier verifier = new PathVerifier();
		path.setInputVerifier(verifier);
		path.getDocument().addDocumentListener(verifier);
		add(path);

		this.errorLabel = new JLabel();
		add(errorLabel);

		this.chooser = new JFileChooser(getPath());
		chooser.setFileSelectionMode(filesAndOrDirectories);
		
		this.fileChooserButton = new JButton("Select");
        fileChooserButton.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					int returnVal = chooser.showOpenDialog(JPathPanel.this);

					if (returnVal == JFileChooser.APPROVE_OPTION) {
						File file = chooser.getSelectedFile();

						setPath(file);
					}
				}
			});
		add(fileChooserButton);
	}

	/**
	 * Just checks if the current path is valid without notifying listeners
	 */
	public boolean verifies() {
		return check();
	}

	/**
	 * Check if current path is valid and notify listeners
	 */
	public boolean verify() {
		if (!check()) {
			notifyErrorListeners();
			return false;
		}
		else {
			chooser.setCurrentDirectory(getPath());
			notifyChangeListeners();
			return true;
		}
	}


	private boolean check() {
		File f = getPath();
		errorLabel.setText(check.errorMessage(f));
		return this.check.verify(f);
	}
	
	public void setBasePath(File basePath) {
		File oldFile = getPath();
		boolean verifies = verifies();
		this.basePath = basePath;
		System.out.println("Changing basePath: " + oldFile + " to " + basePath);
		if (verifies) {
			setPath(oldFile);
		}
		//put this above the if (verifies) to change chooser to the new basedir
		this.chooser.setCurrentDirectory(getPath());
		verify();
	}

	public void setPath(String path) {
		setPath(new File(path));
	}
	
	public void setPath(File path) {
		String pathString;
		if (basePath != null) {
			File relative = RelativePath.getRelativePath(basePath, path);
			//may return absolute path if no relative path possible
			absolute = relative.isAbsolute();
			pathString = relative.toString();
		}
		else {
			pathString = path.getAbsolutePath();
		}

		this.path.setText(pathString);
	}

	/**
	 * get a file representing what this pathpanel is pointing to
	 */
	public File getPath() {
		/*
		 * Build a file object from - if set - the basepath and the textfield content
		 */
		String path = this.path.getText();
		if (path == null) {
			path = "";
		}

		File file;
		if (basePath != null && !absolute) {
			file = new File(basePath.getAbsolutePath() + File.separator + path);
		}
		else {
			file = new File(path);
		}
		return file;
	}


	public void addErrorListener(ErrorListener e) {
		errorListeners.add(e);
	}

	private void notifyErrorListeners() {
		ErrorEvent e = new SimpleErrorEvent(this);
		for (ErrorListener l: errorListeners) {
			l.errorOccured(e);
		}
	}

	public void addChangeListener(ChangeListener l) {
		changeListeners.addChangeListener(l);
	}

	private void notifyChangeListeners() {
		changeListeners.notifyChangeListeners(this);
	}

	/**
	 * Hack: Because i can't call verify() from the inner class that has @Override verify(Stuff s);
	 */
	private boolean verify_() {
		return verify();
	}
	
	

	public interface Verifier {
		public boolean verify(File file);
		public String errorMessage(File file);
	}

	public static class WritableDirectoryVerifier implements Verifier {
		public boolean verify(File f) {
			return (f.exists()
			        && f.isDirectory()
			        && f.canRead()
			        && f.canWrite());
		}
		public String errorMessage(File f) {
			if (!f.exists()) {
				return "Doesn't exist!";
			}
			else if (!f.isDirectory()) {
				return "Is not a directory!";
			}
			else if (!f.canWrite()) {
				return "Cannot be written to!";
			}
			return null;
		}
	}


	private class PathVerifier extends InputVerifier implements DocumentListener {
		@Override
		public void insertUpdate(DocumentEvent e) {
			verify_();
		}
		@Override
		public void removeUpdate(DocumentEvent e) {
			verify_();
		}
		@Override
		public void changedUpdate(DocumentEvent e) {
			verify_();
		}
		@Override
		public boolean verify(JComponent input) {
			return verify_();
		}
		@Override
		public boolean shouldYieldFocus(JComponent input) {
			verify_();
			return true;
		}
	}

}