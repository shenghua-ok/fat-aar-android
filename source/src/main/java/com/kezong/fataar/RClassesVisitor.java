package com.kezong.fataar;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

public class RClassesVisitor extends ClassVisitor {

    private final java.util.Map<String, String> renameMap;

    public RClassesVisitor(int api, ClassVisitor classVisitor, java.util.Map<String, String> renameMap) {
        super(api, classVisitor);
        this.renameMap = renameMap;
    }

    @Override
    public MethodVisitor visitMethod(
            int access,
            String name,
            String descriptor,
            String signature,
            String[] exceptions) {

        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);

        return new MethodVisitor(api, mv) {
            @Override
            public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                String newOwner = renameMap.getOrDefault(owner, owner);
                super.visitFieldInsn(opcode, newOwner, name, descriptor);
            }

            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                String newOwner = renameMap.getOrDefault(owner, owner);
                super.visitMethodInsn(opcode, newOwner, name, descriptor, isInterface);
            }

            @Override
            public void visitLdcInsn(Object value) {
                if (value instanceof Type typeValue) {
                    String internalName = typeValue.getInternalName();
                    String newInternalName = renameMap.get(internalName);
                    if (newInternalName != null) {
                        super.visitLdcInsn(Type.getObjectType(newInternalName));
                        return;
                    }
                }
                super.visitLdcInsn(value);
            }
        };
    }
}
