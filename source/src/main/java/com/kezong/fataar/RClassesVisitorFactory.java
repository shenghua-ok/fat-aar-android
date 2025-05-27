package com.kezong.fataar;

import static org.codehaus.groovy.runtime.DefaultGroovyMethods.println;

import com.android.build.api.instrumentation.AsmClassVisitorFactory;
import com.android.build.api.instrumentation.ClassContext;
import com.android.build.api.instrumentation.ClassData;
import com.android.build.api.instrumentation.InstrumentationParameters;

import org.gradle.api.provider.MapProperty;
import org.gradle.api.tasks.Input;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

import java.util.Map;

public abstract class RClassesVisitorFactory
        implements AsmClassVisitorFactory<RClassesVisitorFactory.RClassesParameters> {

    public interface RClassesParameters extends InstrumentationParameters {
        @Input
        MapProperty<String, String> getTransformTable();
    }

    @Override
    public boolean isInstrumentable(ClassData classData) {
        return classData.getClassName().endsWith(".class");
    }

    @Override
    public @NotNull ClassVisitor createClassVisitor(@NotNull ClassContext classContext, @NotNull ClassVisitor nextClassVisitor) {
        println("mAndroidArchiveLibraries 555555555555555555555555555555555555555555555555555555");
        Map<String, String> table = getParameters().get().getTransformTable().get();
        return new RClassesVisitor(Opcodes.ASM9, nextClassVisitor, table);
    }
}
