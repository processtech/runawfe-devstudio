 
package ru.runa.gpd.bot;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.dom4j.Document;
import org.dom4j.Element;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.ui.internal.wizards.datatransfer.IFileExporter;

import com.google.common.base.Strings;
import com.google.common.base.Throwables;

import ru.runa.gpd.Activator;
import ru.runa.gpd.BotCache;
import ru.runa.gpd.Localization;
import ru.runa.gpd.PluginLogger;
import ru.runa.gpd.ProcessCache;
import ru.runa.gpd.editor.ProcessSaveHistory;
import ru.runa.gpd.lang.model.BotTask;
import ru.runa.gpd.lang.model.ProcessDefinition;
import ru.runa.gpd.lang.model.SubprocessDefinition;
import ru.runa.gpd.lang.par.ProcessDefinitionValidator;
import ru.runa.gpd.sync.WfeServerProcessDefinitionImporter;
import ru.runa.gpd.ui.custom.Dialogs;
import ru.runa.gpd.ui.view.ValidationErrorsView;
import ru.runa.gpd.util.EmbeddedFileUtils;
import ru.runa.gpd.util.XmlUtil;


public class ExportBotGS {
	IFile definitionFile;
	private String FileName;
	private boolean exportToFile;
	private IContainer inputContainer;
	private String fileName;
	private IPath botTaskFile;
	
	public ExportBotGS (IFile definitionFile, String resultName,IPath DirPath, IContainer resultDir, boolean exportToFile) {
		this.exportToFile = exportToFile;
		this.definitionFile = definitionFile;
		this.fileName = resultName;
		this.inputContainer = resultDir;
		this.botTaskFile = DirPath;
	}
	
	public boolean export() {
		
		try {
			
			ProcessDefinition definition =ProcessCache.getProcessDefinition(definitionFile);
            IFolder processFolder = (IFolder) definitionFile.getParent();
            
//            int validationResult = ProcessDefinitionValidator.validateDefinition(definition);
//            if (validationResult != 0) {
//                Activator.getDefault().getWorkbench().getActiveWorkbenchWindow().getActivePage().showView(ValidationErrorsView.ID);
//                if (validationResult == 2) {
//                    //setErrorMessage(Localization.getString("ExportParWizardPage.page.errorsExist"));
//                	return false;
//                }
//            }
//            for (SubprocessDefinition subprocessDefinition : definition.getEmbeddedSubprocesses().values()) {
//                validationResult = ProcessDefinitionValidator.validateDefinition(subprocessDefinition);
//                if (!exportToFile && validationResult != 0) {
//                    if (validationResult == 2) {
//                        //setErrorMessage(Localization.getString("ExportParWizardPage.page.errorsExistInEmbeddedSubprocess"));
//                    	return false;
//                    }
//                }
//            }
            definition.getLanguage().getSerializer().validateProcessDefinitionXML(definitionFile);
            List<IFile> resourcesToExport = new ArrayList<IFile>();
            IResource[] members = processFolder.members();
            for (IResource resource : members) {
                if (resource instanceof IFile) {
                    resourcesToExport.add((IFile) resource);
                }
            }
            // TODO getContainer().run
            if (exportToFile) {
                if (definition.isInvalid()
                        && !Dialogs.confirm(Localization.getString("ExportParWizardPage.confirm.export.invalid.process", definition.getName()))) {
                	PluginLogger.logError(Localization.getString("ExportParWizardPage.confirm.export.invalid.process"),null);
                    return false;
                }                
                String outputFileName ="."+IPath.SEPARATOR+"workspace"+botTaskFile.toOSString()+IPath.SEPARATOR+ fileName + ".glb";
                new ParExportOperation(resourcesToExport, new FileOutputStream(outputFileName)).run(null);
               
                
            } else {
                new ParDeployOperation(resourcesToExport, fileName, true).run(null);
            }
        } catch (Throwable th) {
            PluginLogger.logErrorWithoutDialog(Localization.getString("ExportParWizardPage.error.export"), th);
            
            return false;
        }
		return true;
    }
	public static class ParExportOperation implements IRunnableWithProgress {
        protected final OutputStream outputStream;
        protected final List<IFile> resourcesToExport;

        public ParExportOperation(List<IFile> resourcesToExport, OutputStream outputStream) {
            this.outputStream = outputStream;
            this.resourcesToExport = resourcesToExport;
        }

        protected void exportResource(IFileExporter exporter, IFile fileResource, IProgressMonitor progressMonitor)
                throws IOException, CoreException {
            if (!fileResource.isSynchronized(IResource.DEPTH_ONE)) {
                fileResource.refreshLocal(IResource.DEPTH_ONE, null);
            }
            if (!fileResource.isAccessible()) {
                return;
            }
            String destinationName = fileResource.getName();
            exporter.write(fileResource, destinationName);
        }

        protected void exportResources(IProgressMonitor progressMonitor) {
            try {
                ParFileExporter exporter = new ParFileExporter(outputStream);
                for (IFile resource : resourcesToExport) {
                    exportResource(exporter, resource, progressMonitor);
                    
                }
                exporter.finished();
                outputStream.flush();
            } catch (Exception e) {
                throw Throwables.propagate(e);
            }
        }

        @Override
        public void run(IProgressMonitor progressMonitor) throws InvocationTargetException, InterruptedException {
            exportResources(progressMonitor);
        }
    }

	private class ParDeployOperation extends ParExportOperation {
	    private final String definitionName;
	    private final boolean updateLatestVersion;
	
	    public ParDeployOperation(List<IFile> resourcesToExport, String definitionName, boolean updateLatestVersion) {
	        super(resourcesToExport, new ByteArrayOutputStream());
	        this.definitionName = definitionName;
	        this.updateLatestVersion = updateLatestVersion;
	    }
	
	    @Override
	    public void run(final IProgressMonitor progressMonitor) {
	        exportResources(progressMonitor);
	        final ByteArrayOutputStream baos = (ByteArrayOutputStream) outputStream;
	        WfeServerProcessDefinitionImporter.getInstance().uploadPar(definitionName, updateLatestVersion, baos.toByteArray(), true);
	    }
	}

	private static class ParFileExporter implements IFileExporter {
	    private final ZipOutputStream outputStream;
	
	    public ParFileExporter(OutputStream outputStream) throws IOException {
	        this.outputStream = new ZipOutputStream(outputStream);
	    }
	
	    @Override
	    public void finished() throws IOException {
	        outputStream.close();
	    }
	
	    private void write(ZipEntry entry, IFile contents) throws IOException, CoreException {
	        byte[] readBuffer = new byte[1024];
	        outputStream.putNextEntry(entry);
	        InputStream contentStream = contents.getContents();
	        try {
	            int n;
	            while ((n = contentStream.read(readBuffer)) > 0) {
	                outputStream.write(readBuffer, 0, n);
	            }
	        } finally {
	            if (contentStream != null) {
	                contentStream.close();
	            }
	        }
	        outputStream.closeEntry();
	    }
	
	    @Override
	    public void write(IFile resource, String destinationPath) throws IOException, CoreException {
	        ZipEntry newEntry = new ZipEntry(destinationPath);
	        write(newEntry, resource);
	    }
	
	    @Override
	    public void write(IContainer container, String destinationPath) throws IOException {
	        throw new UnsupportedOperationException();
	    }
	}	
	
}
