package ru.runa.gpd.util;

import com.google.common.base.Charsets;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import com.google.common.io.ByteStreams;
import com.google.common.io.Closeables;
import com.google.common.io.Files;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.eclipse.core.internal.resources.Workspace;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.gef.EditPart;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import ru.runa.gpd.BotCache;
import ru.runa.gpd.BotStationNature;
import ru.runa.gpd.PluginLogger;
import ru.runa.gpd.ProcessProjectNature;
import ru.runa.gpd.form.FormType;
import ru.runa.gpd.form.FormTypeProvider;
import ru.runa.gpd.lang.model.FormNode;
import ru.runa.gpd.lang.model.SubprocessDefinition;
import ru.runa.gpd.lang.par.ParContentProvider;

public class IOUtils {
    private static final ByteArrayInputStream EMPTY_STREAM = new ByteArrayInputStream(new byte[0]);
    private static final List<String> formExtensions = new ArrayList<String>();
    static {
        for (FormType formType : FormTypeProvider.getRegisteredFormTypes()) {
            formExtensions.add(formType.getType());
        }
    }

    public static boolean looksLikeFormFile(String fileName) {
        String ext = getExtension(fileName);
        if (ext.length() == 0) {
            return true;
        }
        if (formExtensions.contains(ext)) {
            return true;
        }
        if (fileName.endsWith(FormNode.VALIDATION_SUFFIX)) {
            return true;
        }
        if (fileName.endsWith(FormNode.SCRIPT_SUFFIX)) {
            return true;
        }
        return false;
    }

    public static String getExtension(String fileName) {
        int lastPointIndex = fileName.lastIndexOf(".");
        if (lastPointIndex == -1) {
            // no extension
            return "";
        }
        return fileName.substring(lastPointIndex + 1);
    }

    public static void copyFileToDir(File sourceFile, File destDir) throws IOException {
        FileInputStream fis = new FileInputStream(sourceFile);
        File destFile = new File(destDir, sourceFile.getName());
        destFile.createNewFile();
        FileOutputStream fos = new FileOutputStream(destFile);
        copyStream(fis, fos);
    }

    public static void copyFile(String source, IFile destinationFile) {
        try {
            copyFile(new FileInputStream(source), destinationFile);
        } catch (FileNotFoundException e) {
            PluginLogger.logErrorWithoutDialog("Unable to copy " + source + " to file " + destinationFile, e);
        }
    }

    public static void copyFile(InputStream source, IFile destinationFile) {
        try {
            createOrUpdateFile(destinationFile, source);
        } catch (Exception e) {
            PluginLogger.logErrorWithoutDialog("Unable to copy to file " + destinationFile, e);
        } finally {
            Closeables.closeQuietly(source);
        }
    }

    public static void copyFile(InputStream source, File destinationFile) {
        try {
            byte[] from = ByteStreams.toByteArray(source);
            Files.write(from, destinationFile);
        } catch (Exception e) {
            PluginLogger.logErrorWithoutDialog("Unable to copy to file " + destinationFile, e);
        } finally {
            Closeables.closeQuietly(source);
        }
    }

    public static String readStream(InputStream in) throws IOException {
        return new String(readStreamAsBytes(in));
    }

    public static byte[] readStreamAsBytes(InputStream in) throws IOException {
        try {
            return ByteStreams.toByteArray(in);
        } finally {
            Closeables.closeQuietly(in);
        }
    }

    public static void copyStream(InputStream in, OutputStream out) throws IOException {
        try {
            ByteStreams.copy(in, out);
        } finally {
            Closeables.closeQuietly(in);
        }
    }

    public static IFile getAdjacentFile(IFile file, String fileName) {
        if (file == null) {
            return null;
        }
        return getFile((IFolder) file.getParent(), fileName);
    }

    public static IFile getFile(IFolder folder, String fileName) {
        IFile file = folder.getFile(fileName);
        if (!file.isSynchronized(IResource.DEPTH_ONE)) {
            try {
                file.refreshLocal(IResource.DEPTH_ONE, null);
            } catch (CoreException e) {
                Throwables.propagate(e);
            }
        }
        return file;
    }

    public static void createFolder(IFolder folder) throws CoreException {
        IContainer parent = folder.getParent();
        if (parent != null && !parent.exists() && parent instanceof IFolder) {
            createFolder((IFolder) parent);
        }
        if (!folder.exists()) {
            folder.create(true, true, null);
        }
    }

    public static void createFile(IFile file) throws CoreException {
        createFile(file, EMPTY_STREAM);
    }

    public static void createFile(IFile file, InputStream stream) throws CoreException {
        if (file.exists()) {
            throw new CoreException(new Status(IStatus.WARNING, "ru.runa.gpd", 0, "File already exist", null));
        }
        file.create(stream, true, null);
        Assert.isTrue(Charsets.UTF_8.name().equalsIgnoreCase(file.getCharset()));
    }

    public static void createOrUpdateFile(IFile file, InputStream stream) throws CoreException {
        if (file.exists()) {
            file.setContents(stream, true, false, null);
        } else {
            file.create(stream, true, null);
            Assert.isTrue(Charsets.UTF_8.name().equalsIgnoreCase(file.getCharset()));
        }
    }

    public static IFile moveFileSafely(IFile file, String fileName) throws CoreException {
        IFolder folder = (IFolder) file.getParent();
        IFile testFile = getFile(folder, fileName);
        try {
            file.move(testFile.getFullPath(), true, null);
            return testFile;
        } catch (CoreException e) {
            // If error caused by many symbols in fileName - decreasing it
            if (fileName.length() < 10) {
                throw e;
            }
            String ext = getExtension(fileName);
            if (ext.length() > 30) {
                // omit extension
                ext = "";
            }
            int index = fileName.indexOf(" ");
            if (index <= 0) {
                index = fileName.length() > 30 ? fileName.length() - ext.length() - 1 : 10;
            }
            fileName = fileName.substring(0, index);
            for (int i = 0; i < 100; i++) {
                String tryFileName = fileName + i;
                if (ext.length() != 0) {
                    tryFileName += "." + ext;
                }
                testFile = getFile(folder, tryFileName);
                if (!testFile.exists()) {
                    break;
                }
            }
            file.move(testFile.getFullPath(), true, null);
            return testFile;
        }
    }

    public static void extractArchiveToFolder(InputStream archiveStream, IFolder folder) throws IOException, CoreException {
        ZipInputStream zis = new ZipInputStream(archiveStream);
        byte[] buf = new byte[1024];
        ZipEntry entry = zis.getNextEntry();
        while (entry != null) {
            if (!entry.getName().contains("META-INF")) {
                IFile file = getFile(folder, entry.getName());
                ByteArrayOutputStream baos = new ByteArrayOutputStream(buf.length);
                int n;
                while ((n = zis.read(buf, 0, 1024)) > -1) {
                    baos.write(buf, 0, n);
                }
                createFile(file, new ByteArrayInputStream(baos.toByteArray()));
            }
            zis.closeEntry();
            entry = zis.getNextEntry();
        }
        zis.close();
    }

    public static Map<String, byte[]> getArchiveFiles(InputStream archiveStream, boolean closeStream) throws IOException {
        Map<String, byte[]> files = new HashMap<String, byte[]>();
        ZipInputStream zis = new ZipInputStream(archiveStream);
        ZipEntry botEntry;
        while ((botEntry = zis.getNextEntry()) != null) {
            byte[] bytes = ByteStreams.toByteArray(zis);
            files.put(botEntry.getName(), bytes);
        }
        zis.close();
        if (closeStream) {
            archiveStream.close();
        }
        return files;
    }

    public static void setUtfCharsetRecursively(IResource resource) throws CoreException {
        if (resource instanceof IProject && !((IProject) resource).isOpen()) {
            return;
        }
        if (resource instanceof IFile) {
            IFile file = (IFile) resource;
            Assert.isTrue(Charsets.UTF_8.name().equalsIgnoreCase(file.getCharset()));
        }
        if (resource instanceof IContainer) {
            for (IResource member : ((IContainer) resource).members()) {
                setUtfCharsetRecursively(member);
            }
        }
    }

    public static IFile getCurrentFile() {
        IWorkbenchWindow activeWindow = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
        if (activeWindow == null || activeWindow.getActivePage() == null) {
            return null;
        }
        IEditorPart editorPart = activeWindow.getActivePage().getActiveEditor();
        if (editorPart == null) {
            return null;
        }
        if (editorPart.getEditorInput() instanceof IFileEditorInput) {
            return ((IFileEditorInput) editorPart.getEditorInput()).getFile();
        }
        return null;
    }

    public static IFile getFile(String fileName) {
        return IOUtils.getAdjacentFile(getCurrentFile(), fileName);
    }

    private static IProject[] getWorkspaceProjects() {
        return ResourcesPlugin.getWorkspace().getRoot().getProjects();
    }

    public static IProject[] getAllBotStationProjects() {
        try {
            List<IProject> projects = new ArrayList<IProject>();
            for (IProject project : getWorkspaceProjects()) {
                if (project.isOpen() && project.getNature(BotStationNature.NATURE_ID) != null) {
                    projects.add(project);
                }
            }
            return projects.toArray(new IProject[0]);
        } catch (CoreException e) {
            throw new RuntimeException();
        }
    }

    public static IProject getBotStationProject(String name) {
        for (IProject botStationProject : getAllBotStationProjects()) {
            if (botStationProject.getName().equals(name)) {
                return botStationProject;
            }
        }

        return null;
    }

    /**
     * Get bot station project for bot folder
     * 
     * @param botFolder
     *            bot folder
     * @return bot station project
     */
    public static IProject getBotStationProjectForBotFolder(IFolder botFolder) {
        if (botFolder.getParent() == null || botFolder.getParent().getParent() == null) {
            return null;
        }

        IContainer container = botFolder.getParent().getParent().getParent();
        return container instanceof IProject ? (IProject) container : null;
    }

    public static List<IFolder> getAllBotFolders() {
        List<IFolder> folderList = new ArrayList<IFolder>();
        for (IProject botStation : getAllBotStationProjects()) {
            folderList.addAll(getBotFolders(botStation));
        }
        return folderList;
    }

    public static List<IFolder> getBotFolders(IProject project) {
        try {
            List<IFolder> folderList = new ArrayList<IFolder>();
            IFolder botFolder = project.getFolder("src/botstation");
            if (!botFolder.exists()) {
                return folderList;
            }

            IResource[] resources = botFolder.members();
            for (int i = 0; i < resources.length; i++) {
                if (resources[i] instanceof IFolder) {
                    folderList.add((IFolder) resources[i]);
                }
            }

            return folderList;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Get bot folder in project by name
     * 
     * @param project
     *            bot station project
     * @param botName
     *            bot name
     * @return bot folder
     */
    public static IFolder getBotFolder(IProject project, String botName) {
        for (IFolder botFolder : getBotFolders(project)) {
            if (botFolder.getName().equals(botName)) {
                return botFolder;
            }
        }

        return null;
    }

    public static List<IFile> getBotTaskFiles(IFolder botFolder) {
        List<IFile> fileList = new ArrayList<IFile>();
        try {
            IResource[] resources = botFolder.members();
            for (int i = 0; i < resources.length; i++) {
                if (resources[i] instanceof IFile && (Strings.isNullOrEmpty(resources[i].getFileExtension())
                        || !(resources[i].getFileExtension().equals(BotCache.CONFIGURATION_FILE_EXTENSION)
                                || resources[i].getFileExtension().equals(BotCache.WORD_TEMPLATE_FILE_EXTENSION)))) {
                    fileList.add((IFile) resources[i]);
                }
            }
        } catch (CoreException e) {
            throw new RuntimeException(e);
        }
        return fileList;
    }

    public static List<IFile> getAllBotTasks() {
        List<IFile> fileList = new ArrayList<IFile>();
        for (IFolder folder : getAllBotFolders()) {
            fileList.addAll(getBotTaskFiles(folder));
        }
        return fileList;
    }

    public static IProject[] getAllProcessDefinitionProjects() {
        List<IProject> result = new ArrayList<IProject>();
        try {
            for (IProject project : getWorkspaceProjects()) {
                if (project.isOpen() && project.getNature(ProcessProjectNature.NATURE_ID) != null) {
                    result.add(project);
                }
            }
        } catch (CoreException e) {
            PluginLogger.logError(e);
        }
        return result.toArray(new IProject[result.size()]);
    }

    public static List<IFile> getAllProcessDefinitionFiles() {
        List<IFile> fileList = new ArrayList<IFile>();
        IProject[] projects = getAllProcessDefinitionProjects();
        for (int i = 0; i < projects.length; i++) {
            if (projects[i].isOpen()) {
                fileList.addAll(getProcessDefinitionFiles(projects[i]));
            }
        }
        return fileList;
    }

    public static List<IFile> getProcessDefinitionFiles(IContainer container) {
        try {
            List<IFile> files = new ArrayList<IFile>();
            findProcessDefinitionsRecursive(container, files);
            return files;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void findProcessDefinitionsRecursive(IResource resource, List<IFile> result) throws CoreException {
        if (resource instanceof IFolder) {
            IFolder folder = (IFolder) resource;
            IFile definitionFile = getProcessDefinitionFile(folder);
            if (definitionFile.exists()) {
                result.add(definitionFile);
                return;
            }
            if (folder.getName().startsWith(".")) {
                return;
            }
            if (folder.getName().equals("bin")) {
                return;
            }
        }
        if (resource instanceof IContainer) {
            IContainer container = (IContainer) resource;
            IResource[] resources = container.members();
            for (int i = 0; i < resources.length; i++) {
                findProcessDefinitionsRecursive(resources[i], result);
            }
        }
    }

    public static List<IContainer> getAllProcessContainers() {
        List<IContainer> result = Lists.newArrayList();
        for (IProject project : getAllProcessDefinitionProjects()) {
            if (isProjectHasProcessNature(project)) {
                findProcessContainers(project, result);
            } else {
                result.add(project.getFolder("src/process"));
            }
        }
        return result;
    }

    private static void findProcessContainers(IResource resource, List<IContainer> result) {
        if (resource instanceof IFolder) {
            IFolder folder = (IFolder) resource;
            if (isProcessDefinitionFolder(folder)) {
                return;
            }
            if (folder.getName().startsWith(".")) {
                return;
            }
        }
        if (resource instanceof IContainer) {
            IContainer container = (IContainer) resource;
            result.add(container);
            try {
                IResource[] resources = container.members();
                for (int i = 0; i < resources.length; i++) {
                    findProcessContainers(resources[i], result);
                }
            } catch (CoreException e) {
                PluginLogger.logError(e);
            }
        }
    }

    public static IFolder getProcessFolder(IContainer container, String definitionName) {
        if (container instanceof IProject) {
            return ((IProject) container).getFolder(definitionName);
        }
        if (container instanceof IFolder) {
            return ((IFolder) container).getFolder(definitionName);
        }
        throw new IllegalArgumentException("Unexpected " + container);
    }

    public static boolean isProjectHasProcessNature(IProject project) {
        try {
            return project != null && project.getNature(ProcessProjectNature.NATURE_ID) != null;
        } catch (CoreException e) {
            throw new RuntimeException(e);
        }
    }

    public static String getProcessContainerName(IContainer container) {
        IProject project = container.getProject();
        if (isProjectHasProcessNature(project)) {
            String path = container.getFullPath().toString();
            if (path.startsWith("/")) {
                path = path.substring(1);
            }
            return path;
        } else {
            return project != null ? project.getName() : "";
        }
    }

    public static boolean isProcessDefinitionFolder(IFolder folder) {
        return getProcessDefinitionFile(folder).exists();
    }
    
    public static boolean isGlobalSectionDefinitionFolder(IFolder folder) {
        return getGlobalSectionDefinitionFile(folder).exists();
    }

    public static IFile getProcessDefinitionFile(IFolder folder) {
        return getFile(folder, ParContentProvider.PROCESS_DEFINITION_FILE_NAME);
    }
    
    public static IFile getGlobalSectionDefinitionFile(IFolder folder) {
        return getFile(folder, ParContentProvider.GLOBAL_SECTION_DEFINITION_FILE_NAME);
    }

    public static IFile getSubprocessDefinitionFile(IFolder folder, SubprocessDefinition definition) {
        return getFile(folder, definition.getId() + "." + ParContentProvider.PROCESS_DEFINITION_FILE_NAME);
    }

    public static IResource getProcessSelectionResource(IStructuredSelection selection) {
        if (selection != null && !selection.isEmpty()) {
            Object selectedElement = selection.getFirstElement();
            if (selectedElement instanceof EditPart) {
                IFile file = IOUtils.getCurrentFile();
                return file == null ? null : file.getParent();
            }
            if (selectedElement instanceof IAdaptable) {
                IAdaptable adaptable = (IAdaptable) selectedElement;
                IResource resource = adaptable.getAdapter(IResource.class);
                if (resource instanceof IProject || resource instanceof IFile) {
                    return resource;
                }
                if (resource instanceof IFolder) {
                    if (isProcessDefinitionFolder((IFolder) resource)) {
                        return resource.getParent();
                    } else {
                        return resource;
                    }
                }
            }
        }
        return null;
    }

    public static boolean isChildFolderExists(IContainer container, String name) {
        if (container instanceof IFolder) {
            return ((IFolder) container).getFolder(name).exists();
        }
        if (container instanceof IProject) {
            return ((IProject) container).getFolder(name).exists();
        }
        return false;
    }

    public static void extractArchiveToProject(InputStream archiveStream, IProject project) throws IOException, CoreException {
        ZipInputStream zis = new ZipInputStream(archiveStream);
        byte[] buf = new byte[1024];
        ZipEntry entry = zis.getNextEntry();
        while (entry != null) {
            if (!entry.getName().contains("META-INF")) {
                IFile file = getFile(project, entry.getName());
                ByteArrayOutputStream baos = new ByteArrayOutputStream(buf.length);
                int n;
                while ((n = zis.read(buf, 0, 1024)) > -1) {
                    baos.write(buf, 0, n);
                }
                createFile(file, new ByteArrayInputStream(baos.toByteArray()));
            }
            zis.closeEntry();
            entry = zis.getNextEntry();
        }
        zis.close();
    }

    public static IFile getFile(IProject project, String fileName) {
        IFile file = project.getFile(fileName);
        if (!file.isSynchronized(IResource.DEPTH_ONE)) {
            try {
                file.refreshLocal(IResource.DEPTH_ONE, null);
            } catch (CoreException e) {
                Throwables.propagate(e);
            }
        }
        return file;
    }

    private static final String DELETED_FILE_EXTENSION = "deleted";

    public static void markAsDeleted(IFile file) throws CoreException {
        IPath deleted = file.getFullPath().addFileExtension(DELETED_FILE_EXTENSION);
        Workspace ws = (Workspace) file.getWorkspace();
        if (ws.getResourceInfo(deleted, false, false) != null) {
            ws.newResource(deleted, IResource.FILE).delete(true, null);
        }
        file.move(deleted, true, null);
    }

    public static void eraseDeletedFiles(IContainer folder) throws CoreException {
        for (IResource member : folder.members()) {
            if (member instanceof IFile) {
                if (((IFile) member).getFileExtension().equals(DELETED_FILE_EXTENSION)) {
                    member.delete(true, null);
                }
            }
        }
    }

    public static void restoreDeletedFiles(IContainer folder) throws CoreException {
        for (IResource member : folder.members()) {
            if (member instanceof IFile) {
                if (((IFile) member).getFileExtension().equals(DELETED_FILE_EXTENSION)) {
                    member.move(member.getFullPath().removeFileExtension(), true, null);
                }
            }
        }
    }

}
