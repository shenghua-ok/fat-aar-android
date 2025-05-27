package com.kezong.fataar;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import java.util.Map;

public class RClassesVisitor extends ClassVisitor {

    private final RClassesVisitorFactory.RClassesParameters argRClasses;

    public RClassesVisitor(int api, ClassVisitor classVisitor, RClassesVisitorFactory.RClassesParameters argRClasses) {
        super(api, classVisitor);
        this.argRClasses = argRClasses;
    }

    @Override
    public MethodVisitor visitMethod(
            int access,
            String name,
            String descriptor,
            String signature,
            String[] exceptions) {

        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
        Map<String, String> table = argRClasses.getTransformTable().get();
        return new MethodVisitor(api, mv) {
            @Override
            public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                String newOwner = table.getOrDefault(owner, owner);
                super.visitFieldInsn(opcode, newOwner, name, descriptor);
            }

            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                String newOwner = table.getOrDefault(owner, owner);
                super.visitMethodInsn(opcode, newOwner, name, descriptor, isInterface);
            }

            @Override
            public void visitLdcInsn(Object value) {
                if (value instanceof Type typeValue) {
                    String internalName = typeValue.getInternalName();
                    String newInternalName = table.get(internalName);
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
