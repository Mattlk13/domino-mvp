package com.progressoft.brix.domino.apt.client.processors.group;

import com.google.auto.service.AutoService;
import com.progressoft.brix.domino.api.client.annotations.RequestFactory;
import com.progressoft.brix.domino.apt.commons.BaseProcessor;
import com.progressoft.brix.domino.apt.commons.ProcessorElement;

import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.io.Writer;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;


@AutoService(Processor.class)
public class RequestGroupProcessor extends BaseProcessor {

    private static final Logger LOGGER = Logger.getLogger(RequestGroupProcessor.class.getName());

    private final Set<String> supportedAnnotations = new HashSet<>();

    public RequestGroupProcessor() {
        supportedAnnotations.add(RequestFactory.class.getCanonicalName());
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return supportedAnnotations;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }


    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        roundEnv.getElementsAnnotatedWith(RequestFactory.class)
                .stream().filter(e -> ElementKind.INTERFACE.equals(e.getKind()))
                .forEach(this::generateRequestGroupFactory);
        return false;
    }

    private void generateRequestGroupFactory(Element element) {
        ProcessorElement processorElement = newProcessorElement(element);
        try (Writer sourceWriter = obtainSourceWriter(
                processorElement.elementPackage(), processorElement.simpleName() + "Factory")) {
            sourceWriter
                    .write(new RequestGroupSourceWriter(processorElement).write());
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Could not generate classes : ", e);
            messager.printMessage(Diagnostic.Kind.ERROR, "could not generate class");
        }
    }
}
