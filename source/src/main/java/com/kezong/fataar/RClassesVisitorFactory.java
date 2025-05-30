package com.kezong.fataar;

import com.android.build.api.instrumentation.AsmClassVisitorFactory;
import com.android.build.api.instrumentation.ClassContext;
import com.android.build.api.instrumentation.ClassData;
import com.android.build.api.instrumentation.InstrumentationParameters;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

public abstract class RClassesVisitorFactory
        implements AsmClassVisitorFactory<RClassesVisitorFactory.RClassesParameters> {
    public interface RClassesParameters extends InstrumentationParameters {
        @InputFile
        @PathSensitive(PathSensitivity.RELATIVE)
        RegularFileProperty getTransformTableFile();
    }

    @Override
    public boolean isInstrumentable(ClassData classData) {
        return true;
    }

    @Override
    public @NotNull ClassVisitor createClassVisitor(@NotNull ClassContext classContext, @NotNull ClassVisitor nextClassVisitor) {
        RegularFileProperty fileProperty = getParameters().get().getTransformTableFile();
        return new RClassesVisitor(Opcodes.ASM9, nextClassVisitor, fileProperty);
    }
}
