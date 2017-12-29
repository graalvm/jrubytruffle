/*
 * Copyright (c) 2013, 2017 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.truffleruby.language.loader;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.source.Source;
import org.truffleruby.Log;
import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.core.string.StringUtils;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.control.RaiseException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Locale;

public class SourceLoader {

    public static final String RESOURCE_SCHEME = "resource:";

    private final RubyContext context;

    public SourceLoader(RubyContext context) {
        this.context = context;
    }

    @TruffleBoundary
    public Source loadMainEval() {
        return Source.newBuilder(context.getOptions().TO_EXECUTE).name(
                "-e").mimeType(RubyLanguage.MIME_TYPE).build();
    }

    @TruffleBoundary
    public Source loadMainStdin(RubyNode currentNode, String path) throws IOException {
        return Source.newBuilder(xOptionStrip(
                currentNode,
                new InputStreamReader(System.in))).name(path).mimeType(RubyLanguage.MIME_TYPE).build();
    }

    @TruffleBoundary
    public Source loadMainFile(RubyNode currentNode, String path) throws IOException {
        final File file = new File(path).getCanonicalFile();
        ensureReadable(path, file);

        return Source.newBuilder(file).name(path).content(xOptionStrip(
                currentNode,
                new FileReader(file))).mimeType(RubyLanguage.MIME_TYPE).build();
    }

    private String xOptionStrip(RubyNode currentNode, Reader reader) throws IOException {
        StringBuilder content = new StringBuilder();
        try (BufferedReader bufferedReader = new BufferedReader(reader)) {

            boolean lookForRubyShebang = isCurrentLineShebang(bufferedReader) ||
                    context.getOptions().IGNORE_LINES_BEFORE_RUBY_SHEBANG;

            if (lookForRubyShebang) {
                while (true) {
                    final String line = bufferedReader.readLine();
                    if (line == null) {
                        throw new RaiseException(context.getCoreExceptions().loadError(
                                "no Ruby script found in input",
                                "",
                                currentNode));
                    }

                    final boolean rubyShebang = line.startsWith("#!") && line.contains("ruby");
                    if (rubyShebang) {
                        content.append(line);
                        content.append("\n");
                        break;
                    } else {
                        content.append("# line ignored by Ruby:"); // prefix with a comment so it's ignored by parser
                        content.append(line);
                        content.append("\n");
                    }
                }
            }

            final char[] buffer = new char[1024];
            while (true) {
                final int read = bufferedReader.read(buffer, 0, buffer.length);
                if (read < 0) {
                    break;
                } else {
                    content.append(buffer, 0, read);
                }
            }

            return content.toString();
        }
    }

    private boolean isCurrentLineShebang(BufferedReader bufferedReader) throws IOException {
        final char[] buffer = new char[2];
        bufferedReader.mark(2);
        bufferedReader.read(buffer, 0, 2);
        bufferedReader.reset();
        return buffer[0] == '#' && buffer[1] == '!';
    }

    @TruffleBoundary
    public Source load(String canonicalPath) throws IOException {
        if (context.getOptions().LOG_LOAD) {
            Log.LOGGER.info("loading " + canonicalPath);
        }

        if (canonicalPath.startsWith(RESOURCE_SCHEME)) {
            return loadResource(canonicalPath);
        } else {
            final File file = new File(canonicalPath).getCanonicalFile();
            ensureReadable(canonicalPath, file);

            final String mimeType;

            if (canonicalPath.toLowerCase().endsWith(RubyLanguage.CEXT_EXTENSION)) {
                mimeType = RubyLanguage.CEXT_MIME_TYPE;
            } else {
                // We need to assume all other files are Ruby, so the file type detection isn't
                // enough
                mimeType = RubyLanguage.MIME_TYPE;
            }

            Source.Builder<IOException, RuntimeException, RuntimeException> builder =
                    Source.newBuilder(file).name(file.getPath()).mimeType(mimeType);

            if (isInternal(canonicalPath)) {
                builder = builder.internal();
            }

            return builder.build();
        }
    }

    private boolean isInternal(String canonicalPath) {
        if (canonicalPath.startsWith(context.getCoreLibrary().getCoreLoadPath())) {
            return true;
        }

        if (canonicalPath.startsWith(context.getRubyHome())) {
            return true;
        }

        return false;
    }

    @TruffleBoundary
    public static Source loadResource(String path) throws IOException {
        if (TruffleOptions.AOT) {
            if (!path.startsWith(SourceLoader.RESOURCE_SCHEME)) {
                throw new UnsupportedOperationException();
            }

            final String canonicalPath = SourceLoaderSupport.canonicalizeResourcePath(path);
            final SourceLoaderSupport.CoreLibraryFile coreFile = SourceLoaderSupport.allCoreLibraryFiles.get(
                    canonicalPath);
            if (coreFile == null) {
                throw new FileNotFoundException(path);
            }

            return Source.newBuilder(coreFile.code).name(path).mimeType(RubyLanguage.MIME_TYPE).internal().build();
        } else {
            if (!path.toLowerCase(Locale.ENGLISH).endsWith(".rb")) {
                throw new FileNotFoundException(path);
            }

            final Class<?> relativeClass;
            final Path relativePath;

            if (path.startsWith(RESOURCE_SCHEME)) {
                relativeClass = RubyContext.class;
                relativePath = FileSystems.getDefault().getPath(path.substring(RESOURCE_SCHEME.length()));
            } else {
                throw new UnsupportedOperationException();
            }

            final Path normalizedPath = relativePath.normalize();
            final InputStream stream = relativeClass.getResourceAsStream(
                    StringUtils.replace(normalizedPath.toString(), '\\', '/'));

            if (stream == null) {
                throw new FileNotFoundException(path);
            }

            return Source.newBuilder(new InputStreamReader(stream, StandardCharsets.UTF_8)).name(path).
                    mimeType(RubyLanguage.MIME_TYPE).internal().build();
        }
    }

    private void ensureReadable(String path, File file) {
        if (!file.exists()) {
            throw new RaiseException(context.getCoreExceptions().loadError("No such file or directory -- " + path, path, null));
        }

        if (!file.canRead()) {
            throw new RaiseException(context.getCoreExceptions().loadError("Permission denied -- " + path, path, null));
        }
    }

}
