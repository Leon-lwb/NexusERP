package com.nexus.runtime.attestation;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

/** 固定根和发布输入必须由操作系统保护；当前进程可写或无法判定时拒绝。 */
final class ProtectedFiles {
    private static final Set<String> WINDOWS_OWNERS = Set.of("NT AUTHORITY\\SYSTEM", "BUILTIN\\Administrators");
    private static final Set<AclEntryPermission> WRITES = Set.of(AclEntryPermission.WRITE_DATA, AclEntryPermission.APPEND_DATA,
        AclEntryPermission.WRITE_ATTRIBUTES, AclEntryPermission.WRITE_NAMED_ATTRS, AclEntryPermission.DELETE,
        AclEntryPermission.DELETE_CHILD, AclEntryPermission.WRITE_ACL, AclEntryPermission.WRITE_OWNER);
    private ProtectedFiles() { }
    static Path requireProtected(Path input) {
        try {
            Path absolute = input.toAbsolutePath().normalize();
            for (Path path = absolute; path != null; path = path.getParent()) {
                if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path) || Files.isWritable(path)) throw denied();
                var posix = Files.getFileAttributeView(path, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
                if (posix != null) {
                    var attrs = posix.readAttributes();
                    if (!attrs.owner().getName().equals("root") || attrs.permissions().contains(PosixFilePermission.GROUP_WRITE)
                        || attrs.permissions().contains(PosixFilePermission.OTHERS_WRITE)) throw denied();
                } else {
                    var view = Files.getFileAttributeView(path, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
                    if (view == null || !WINDOWS_OWNERS.contains(view.getOwner().getName())) throw denied();
                    for (var entry : view.getAcl()) if (entry.type() == AclEntryType.ALLOW
                        && !WINDOWS_OWNERS.contains(entry.principal().getName())
                        && entry.permissions().stream().anyMatch(WRITES::contains)) throw denied();
                }
            }
            if (!absolute.equals(absolute.toRealPath())) throw denied();
            return absolute;
        } catch (AttestationFailure failure) { throw failure; }
        catch (Exception ignored) { throw denied(); }
    }
    private static AttestationFailure denied() { return new AttestationFailure("ATTESTATION_IMMUTABLE_FILES_UNPROVEN"); }
}
