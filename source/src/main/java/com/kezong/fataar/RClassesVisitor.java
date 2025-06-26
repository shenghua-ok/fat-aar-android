package com.kezong.fataar;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.gradle.api.file.RegularFileProperty;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import java.io.File;
import java.io.IOException;
import java.util.Map;

public class RClassesVisitor extends ClassVisitor {
    private final RegularFileProperty fileProperty;
    private Map<String, String> cachedMappingTable;

    public RClassesVisitor(int api, ClassVisitor classVisitor, RegularFileProperty fileProperty) {
        super(api, classVisitor);
        this.fileProperty = fileProperty;
    }

    @Override
    public MethodVisitor visitMethod(
            int access,
            String name,
            String descriptor,
            String signature,
            String[] exceptions) {
        if (cachedMappingTable == null) {
            File mappingFile = fileProperty.get().getAsFile();
            cachedMappingTable = readMappingFile(mappingFile);
        }
        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);

        return new MethodVisitor(api, mv) {
            @Override
            public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                String newOwner = cachedMappingTable.getOrDefault(owner, owner);
                super.visitFieldInsn(opcode, newOwner, name, descriptor);
            }

            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                String newOwner = cachedMappingTable.getOrDefault(owner, owner);
                super.visitMethodInsn(opcode, newOwner, name, descriptor, isInterface);
            }

            @Override
            public void visitLdcInsn(Object value) {
                if (value instanceof Type typeValue) {
                    String internalName = typeValue.getInternalName();
                    String newInternalName = cachedMappingTable.get(internalName);
                    if (newInternalName != null) {
                        super.visitLdcInsn(Type.getObjectType(newInternalName));
                        return;
                    }
                }
                super.visitLdcInsn(value);
            }
        };
    }

    private static Map<String, String> readMappingFile(File file) {
        try {
            String json = new String(java.nio.file.Files.readAllBytes(file.toPath()));
            TypeToken<Map<String, String>> typeToken = new TypeToken<>() {
            };
            return new Gson().fromJson(json, typeToken.getType());
        } catch (IOException e) {
            throw new RuntimeException("[fat-aar] Failed to read transform table JSON file", e);
        }
    }
}
