/*
 * ====================================================================
 * 
 * The ObjectStyle Group Software License, Version 1.0
 * 
 * Copyright (c) 2006 The ObjectStyle Group and individual authors of the
 * software. All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 * 
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * 
 * 3. The end-user documentation included with the redistribution, if any, must
 * include the following acknowlegement: "This product includes software
 * developed by the ObjectStyle Group (http://objectstyle.org/)." Alternately,
 * this acknowlegement may appear in the software itself, if and wherever such
 * third-party acknowlegements normally appear.
 * 
 * 4. The names "ObjectStyle Group" and "Cayenne" must not be used to endorse or
 * promote products derived from this software without prior written permission.
 * For written permission, please contact andrus@objectstyle.org.
 * 
 * 5. Products derived from this software may not be called "ObjectStyle" nor
 * may "ObjectStyle" appear in their names without prior written permission of
 * the ObjectStyle Group.
 * 
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * OBJECTSTYLE GROUP OR ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA,
 * OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,
 * EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 * ====================================================================
 * 
 * This software consists of voluntary contributions made by many individuals on
 * behalf of the ObjectStyle Group. For more information on the ObjectStyle
 * Group, please see <http://objectstyle.org/>.
 *  
 */
package org.objectstyle.wolips.eogenerator.ui.actions;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.LinkedList;
import java.util.List;

import org.eclipse.core.internal.resources.ResourceException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IObjectActionDelegate;
import org.eclipse.ui.IWorkbenchPart;
import org.objectstyle.wolips.eogenerator.ui.dialogs.EOGeneratorResultsDialog;
import org.objectstyle.wolips.eogenerator.ui.editors.CommandLineTokenizer;
import org.objectstyle.wolips.eogenerator.ui.editors.EOGeneratorEditor;
import org.objectstyle.wolips.eogenerator.ui.editors.EOGeneratorModel;
import org.objectstyle.wolips.preferences.Preferences;

public class GenerateAction implements IObjectActionDelegate {
  private ISelection mySelection;

  public GenerateAction() {
    super();
  }

  public void setActivePart(IAction _action, IWorkbenchPart _targetPart) {
  }

  public void run(IAction _action) {
    try {
      IStructuredSelection selection = (IStructuredSelection) mySelection;
      if (selection != null && !selection.isEmpty()) {
        IFile eogenFile = (IFile) selection.getFirstElement();
        EOGenerateWorkspaceJob generateJob = new EOGenerateWorkspaceJob(eogenFile);
        generateJob.schedule();
      }
    }
    catch (Throwable t) {
      t.printStackTrace();
      MessageDialog.openError(new Shell(), "Generate Failed", t.getMessage());
    }
  }

  public void selectionChanged(IAction _action, ISelection _selection) {
    mySelection = _selection;
  }

  protected static class EOGenerateWorkspaceJob extends WorkspaceJob {
    private IFile myEOGenFile;

    public EOGenerateWorkspaceJob(IFile _eogenFile) {
      super("EOGenerating " + _eogenFile.getName() + " ...");
      myEOGenFile = _eogenFile;
    }

    public IStatus runInWorkspace(IProgressMonitor _monitor) throws CoreException {
      try {
        EOGeneratorModel eogenModel = EOGeneratorEditor.createEOGeneratorModel(myEOGenFile);
        String eogenFileContents = eogenModel.writeToString(Preferences.getEOGeneratorPath(), Preferences.getEOGeneratorTemplateDir(), Preferences.getEOGeneratorJavaTemplate(), Preferences.getEOGeneratorSubclassJavaTemplate());
        List commandsList = new LinkedList();
        CommandLineTokenizer tokenizer = new CommandLineTokenizer(eogenFileContents);
        while (tokenizer.hasMoreTokens()) {
          commandsList.add(tokenizer.nextToken());
        }
        String[] tokens = (String[]) commandsList.toArray(new String[commandsList.size()]);
        IProject project = myEOGenFile.getProject();
        Process process = Runtime.getRuntime().exec(tokens, null, project.getLocation().toFile());

        InputStream inputstream = process.getInputStream();
        InputStreamReader inputstreamreader = new InputStreamReader(inputstream);
        BufferedReader bufferedreader = new BufferedReader(inputstreamreader);

        StringBuffer output = new StringBuffer();
        output.append("EOGenerator Finished!\n\n");
        
        String line;
        while ((line = bufferedreader.readLine()) != null) {
          output.append(line);
        }

        try {
          if (process.waitFor() != 0) {
            output.append("\n\nExit value = " + process.exitValue());
          }
        }
        catch (InterruptedException e) {
          System.err.println(e);
        }
        
        project.getFolder(new Path(eogenModel.getDestination())).refreshLocal(IResource.DEPTH_INFINITE, _monitor);
        project.getFolder(new Path(eogenModel.getSubclassDestination())).refreshLocal(IResource.DEPTH_INFINITE, _monitor);

        final String outputStr = output.toString();
        Display.getDefault().asyncExec(new Runnable() {
          public void run() {
            EOGeneratorResultsDialog resultsDialog = new EOGeneratorResultsDialog(new Shell(), outputStr);
            resultsDialog.open();
          }
        });
      }
      catch (Throwable t) {
        throw new ResourceException(IStatus.ERROR, myEOGenFile.getFullPath(), "Failed to generate.", t);
      }
      return new Status(IStatus.OK, org.objectstyle.wolips.eogenerator.ui.Activator.PLUGIN_ID, IStatus.OK, "Done", null);
    }
  }
}
