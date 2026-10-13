package com.nexus.report.storage;
import com.nexus.report.domain.ReportFault;
import com.nexus.report.service.ReportDefinitionService;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.net.InetAddress;
import java.io.*;
import java.security.*;
import java.util.*;
/** Application-private, tenant-scoped filesystem. Never mounted as static/public resources. */
public final class LocalReportArtifactStore implements ReportArtifactStore {
    private static final Set<String> KINDS=Set.of("rows","json","plan","pdf","xlsx");
    private final Path root;private final long maxBytes;private final String storeId;
    public LocalReportArtifactStore(Path configured,long maxBytes){
        if(maxBytes<=0||maxBytes>200L*1024*1024)throw new IllegalArgumentException("Artifact budget required");
        this.root=configured.toAbsolutePath().normalize();this.maxBytes=maxBytes;
        try{rejectLinks(root);Files.createDirectories(root);privateAccess(root);this.storeId="local-"+ReportDefinitionService.hash(InetAddress.getLocalHost().getHostName()+"|"+root.toRealPath()).substring(0,24);}catch(IOException e){throw fault();}
    }
    private DirectoryStream<Path> recoveryFolders, recoveryFiles;
    private Iterator<Path> folderCursor, fileCursor;
    private String recoveryTenant;
    public String storeId(){return storeId;}
    private static void rejectLinks(Path path){Path current=path.getRoot();for(Path part:path){current=current.resolve(part);if(Files.isSymbolicLink(current))throw new ReportFault("ARTIFACT_INVALID","私有报表路径不能包含符号链接",500);}}
    private Path tenant(String tenant){
        if(tenant==null||!tenant.matches("[A-Za-z0-9_-]{1,20}"))throw new ReportFault("ARTIFACT_INVALID","租户路径无效",400);
        Path path=root.resolve(tenant).normalize();if(!path.startsWith(root))throw fault();
        try{rejectLinks(path);Files.createDirectories(path);privateAccess(path);return path;}catch(IOException e){throw fault();}
    }
    private static void privateAccess(Path file)throws IOException {
        var posix=Files.getFileAttributeView(file,PosixFileAttributeView.class,LinkOption.NOFOLLOW_LINKS);
        if(posix!=null){posix.setPermissions(Files.isDirectory(file)?PosixFilePermissions.fromString("rwx------"):PosixFilePermissions.fromString("rw-------"));return;}
        var acl=Files.getFileAttributeView(file,AclFileAttributeView.class,LinkOption.NOFOLLOW_LINKS);
        if(acl!=null){var owner=Files.getOwner(file,LinkOption.NOFOLLOW_LINKS);var entry=AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(owner).setPermissions(EnumSet.allOf(AclEntryPermission.class)).build();acl.setAcl(List.of(entry));return;}
        throw new IOException("Private file permissions unavailable");
    }
    private static void kind(String kind){if(!KINDS.contains(kind))throw new ReportFault("ARTIFACT_INVALID","报表文件类型无效");}
    public Path temporary(String tenant,String kind){kind(kind);try{Path path=Files.createTempFile(tenant(tenant),"pending-","."+kind+".tmp");privateAccess(path);return path;}catch(IOException e){throw fault();}}
    private Path ownTemporary(String tenant,Path path){Path folder=tenant(tenant);Path absolute=path.toAbsolutePath().normalize();if(!absolute.getParent().equals(folder)||!absolute.getFileName().toString().matches("pending-[A-Za-z0-9_-]+\\.(rows|json|plan|pdf|xlsx)\\.tmp")||Files.isSymbolicLink(absolute))throw new ReportFault("ARTIFACT_INVALID","文件不属于当前报表租户");return absolute;}
    public OutputStream output(String tenant,Path temporary){Path path=ownTemporary(tenant,temporary);if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))throw fault();
        try{return new FilterOutputStream(Files.newOutputStream(path,StandardOpenOption.TRUNCATE_EXISTING,StandardOpenOption.WRITE)){
            private long count;
            private void budget(int size){if(size<0||count+size>maxBytes)throw ReportFault.capacity();count+=size;}
            public void write(int value)throws IOException{budget(1);out.write(value);}
            public void write(byte[] bytes,int offset,int length)throws IOException{Objects.checkFromIndexSize(offset,length,bytes.length);budget(length);out.write(bytes,offset,length);}
        };}catch(IOException e){throw fault();}
    }
    public String commit(String tenant,Path temporary,String kind){kind(kind);Path path=ownTemporary(tenant,temporary);try{if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||Files.size(path)>maxBytes)throw ReportFault.capacity();String key=UUID.randomUUID()+"."+kind;Path target=tenant(tenant).resolve(key);try{Files.move(path,target,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(path,target);}privateAccess(target);return key;}catch(IOException e){throw fault();}}
    private Path object(String tenant,String reference){if(reference==null||!reference.matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}\\.(rows|json|plan|pdf|xlsx)"))throw new ReportFault("ARTIFACT_INVALID","报表对象标识无效");Path path=tenant(tenant).resolve(reference);if(Files.isSymbolicLink(path))throw fault();return path;}
    public Path read(String tenant,String reference){Path path=object(tenant,reference);if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))throw new ReportFault("SNAPSHOT_EXPIRED","报表文件已过期或不在当前存储节点",410);return path;}
    public boolean exists(String tenant,String reference){return Files.isRegularFile(object(tenant,reference),LinkOption.NOFOLLOW_LINKS);}
    public void delete(String tenant,String reference){if(reference==null)return;try{Files.deleteIfExists(object(tenant,reference));}catch(IOException e){throw fault();}}
    public void discard(String tenant,Path temporary){if(temporary==null)return;try{Files.deleteIfExists(ownTemporary(tenant,temporary));}catch(IOException e){throw fault();}}
    public String checksum(String tenant,String reference){try(var input=Files.newInputStream(read(tenant,reference))){var digest=MessageDigest.getInstance("SHA-256");byte[] bytes=new byte[8192];int size;while((size=input.read(bytes))>=0)digest.update(bytes,0,size);return HexFormat.of().formatHex(digest.digest());}catch(IOException|NoSuchAlgorithmException e){throw fault();}}
    /** A bounded cursor advances across retained objects and tenants instead of restarting at the head. */
    public synchronized void reap(java.time.Instant olderThan, java.util.function.BiPredicate<String, String> referenced) {
        Objects.requireNonNull(olderThan); Objects.requireNonNull(referenced);
        try {
            if (recoveryFolders == null) { recoveryFolders = Files.newDirectoryStream(root); folderCursor = recoveryFolders.iterator(); }
            for (int examined = 0; examined < 1000;) {
                if (recoveryFiles != null) {
                    if (fileCursor.hasNext()) { examined++; reapFile(fileCursor.next(), olderThan, referenced); continue; }
                    recoveryFiles.close(); recoveryFiles = null; fileCursor = null; recoveryTenant = null;
                }
                if (!folderCursor.hasNext()) { closeRecovery(); return; }
                Path folder = folderCursor.next(); examined++;
                String tenantId = folder.getFileName().toString();
                if (!tenantId.matches("[A-Za-z0-9_-]{1,20}") || Files.isSymbolicLink(folder) || !Files.isDirectory(folder, LinkOption.NOFOLLOW_LINKS)) continue;
                try { recoveryFiles = Files.newDirectoryStream(folder); }
                catch (NoSuchFileException | NotDirectoryException concurrentCleanup) { continue; }
                fileCursor = recoveryFiles.iterator(); recoveryTenant = tenantId;
            }
        } catch (IOException | DirectoryIteratorException failure) {
            resetAfterFailure(failure); throw fault();
        } catch (RuntimeException failure) { resetAfterFailure(failure); throw failure; }
    }
    private void reapFile(Path file, java.time.Instant olderThan, java.util.function.BiPredicate<String, String> referenced) throws IOException {
        if (Files.isSymbolicLink(file) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) return;
        java.time.Instant modified;
        try { modified = Files.getLastModifiedTime(file, LinkOption.NOFOLLOW_LINKS).toInstant(); }
        catch (NoSuchFileException concurrentCleanup) { return; }
        if (!modified.isBefore(olderThan)) return;
        String name = file.getFileName().toString();
        if (name.matches("pending-[A-Za-z0-9_-]+\\.(rows|json|plan|pdf|xlsx)\\.tmp")) discard(recoveryTenant, file);
        else if (name.matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}\\.(rows|json|plan|pdf|xlsx)")
            && !referenced.test(recoveryTenant, name)) delete(recoveryTenant, name);
    }
    private void resetAfterFailure(Throwable original) {
        try { closeRecovery(); } catch (IOException cleanup) { original.addSuppressed(cleanup); }
    }
    private void closeRecovery() throws IOException {
        var files = recoveryFiles; var folders = recoveryFolders;
        recoveryFiles = null; recoveryFolders = null; fileCursor = null; folderCursor = null; recoveryTenant = null;
        IOException failure = null;
        try { if (files != null) files.close(); } catch (IOException error) { failure = error; }
        try { if (folders != null) folders.close(); } catch (IOException error) { if (failure == null) failure = error; else failure.addSuppressed(error); }
        if (failure != null) throw failure;
    }
    @Override public synchronized void close() {
        try { closeRecovery(); } catch (IOException failure) { throw fault(); }
    }
    private static ReportFault fault(){return new ReportFault("ARTIFACT_STORE_UNAVAILABLE","私有报表文件存储不可用",500);}
}
