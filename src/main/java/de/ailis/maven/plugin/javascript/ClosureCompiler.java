/*
 * Copyright (C) 2011 Klaus Reimer <k@ailis.de>
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package de.ailis.maven.plugin.javascript;

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import org.codehaus.plexus.compiler.CompilerConfiguration;
import org.codehaus.plexus.logging.Logger;

import com.google.javascript.jscomp.AnonymousFunctionNamingPolicy;
import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.ClosureCodingConvention;
import com.google.javascript.jscomp.CompilationLevel;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.CompilerOptions.TracerMode;
import com.google.javascript.jscomp.CompilerOptions.TweakProcessing;
import com.google.javascript.jscomp.DiagnosticGroups;
import com.google.javascript.jscomp.ErrorFormat;
import com.google.javascript.jscomp.JSSourceFile;
import com.google.javascript.jscomp.MessageFormatter;
import com.google.javascript.jscomp.PropertyRenamingPolicy;
import com.google.javascript.jscomp.Result;
import com.google.javascript.jscomp.SourceMap;
import com.google.javascript.jscomp.VariableRenamingPolicy;
import com.google.javascript.jscomp.WarningLevel;

/**
 * A small wrapper around the closure compiler. The main purpose is having it
 * under a different name so it doesn't conflict with the Plexus Compiler
 * interface but this class also configures all the compiler options and the
 * logger.
 *
 * @author Klaus Reimer (k@ailis.de)
 */
public class ClosureCompiler extends Compiler
{
    /** Te compiler options. */
    private final CompilerOptions options;

    /**
     * Constructor.
     *
     * @param logger
     *            The plexus logger.
     * @param config
     *            The compiler configuration.
     */
    public ClosureCompiler(final Logger logger, final CompilerConfiguration config)
    {
        super();
        this.options = createCompilerOptions();
        if (logger != null)
        {
            final MessageFormatter formatter = this.options.errorFormat
                .toFormatter(this, false);
            final PlexusErrorManager errorManager = new PlexusErrorManager(
                formatter, logger,
                new File(config.getSourceLocations().get(0).toString()));
            errorManager.setPrintMessages(config.isVerbose());
            setErrorManager(errorManager);
        }
    }

    /**
     * Creates and returns the compiler options.
     *
     * @return The compiler options
     */
    private CompilerOptions createCompilerOptions()
    {
        final CompilerOptions options = new CompilerOptions();
        CompilationLevel.SIMPLE_OPTIMIZATIONS
            .setOptionsForCompilationLevel(options);
        WarningLevel.VERBOSE.setOptionsForWarningLevel(options);

        options.aggressiveVarCheck = CheckLevel.ERROR;
        options.aliasableGlobals = null;
        options.aliasableStrings = Collections.emptySet();
        options.aliasAllStrings = false;
        options.aliasExternals = false;
        options.aliasKeywords = false;
        options.aliasStringsBlacklist = "";
        options.allowLegacyJsMessages = false;
        options.ambiguateProperties = false;
        options.anonymousFunctionNaming = AnonymousFunctionNamingPolicy.OFF;
        options.appNameStr = "";

        options.brokenClosureRequiresLevel = CheckLevel.ERROR;

        options.checkCaja = false;
        options.checkControlStructures = true;
        options.checkDuplicateMessages = true;
        options.checkEs5Strict = true;
        options.checkFunctions = CheckLevel.ERROR;
        options.checkGlobalNamesLevel = CheckLevel.ERROR;
        options.checkGlobalThisLevel = CheckLevel.ERROR;
        options.checkMethods = CheckLevel.ERROR;
        options.checkMissingGetCssNameBlacklist = null;
        options.checkMissingGetCssNameLevel = CheckLevel.OFF;
        options.checkMissingReturn = CheckLevel.ERROR;
        options.checkProvides = CheckLevel.OFF;
        options.checkRequires = CheckLevel.OFF;
        options.checkShadowVars = CheckLevel.WARNING;
        options.checkSuspiciousCode = false;
        options.checkSymbols = true;
        options.checkTypedPropertyCalls = true;
        options.checkTypes = true;
        options.checkUnreachableCode = CheckLevel.WARNING;
        options.closurePass = false;
        options.coalesceVariableNames = false;
        options.collapseAnonymousFunctions = false;
        options.collapseProperties = false;
        options.collapseVariableDeclarations = true;
        options.computeFunctionSideEffects = true;
        options.convertToDottedProperties = true;
        options.crossModuleCodeMotion = false;
        options.crossModuleMethodMotion = false;
        options.cssRenamingMap = null;
        options.customPasses = null;

        options.deadAssignmentElimination = true;
        options.debugFunctionSideEffectsPath = null;
        options.decomposeExpressions = false;
        options.devirtualizePrototypeMethods = false;
        options.disambiguateProperties = false;

        options.disableRuntimeTypeCheck();

        options.errorFormat = ErrorFormat.SINGLELINE;
        options.exportTestFunctions = false;
        options.extractPrototypeMemberDeclarations = false;
        options.extraAnnotationNames = new HashSet<String>();
        options.extraAnnotationNames.add("require");
        options.extraAnnotationNames.add("use");
        options.extraAnnotationNames.add("provide");

        options.enableExternExports(false);

        options.flowSensitiveInlineVariables = false;
        options.foldConstants = true;

        options.gatherCssNames = false;
        options.generateExports = false;
        options.generatePseudoNames = false;
        options.groupVariableDeclarations = true;

        options.ideMode = false;
        options.ignoreCajaProperties = false;
        options.inferTypesInGlobalScope = true;
        options.inlineAnonymousFunctionExpressions = false;
        options.inlineConstantVars = false;
        options.inlineFunctions = false;
        options.inlineGetters = false;
        options.inlineLocalVariables = false;
        options.inlineLocalFunctions = false;
        options.inlineVariables = false;
        options.inputDelimiter = "// Input %num%";
        options.inputPropertyMapSerialized = null;
        options.inputVariableMapSerialized = null;
        options.instrumentationTemplate = null;
        options.instrumentForCoverage = false;
        options.instrumentForCoverageOnly = false;

        options.labelRenaming = false;
        options.lineBreak = true;
        options.locale = "UTF-8";

        options.markAsCompiled = false;
        options.markNoSideEffectCalls = false;
        options.messageBundle = null;
        options.moveFunctionDeclarations = false;

        options.nameReferenceGraphPath = null;
        options.nameReferenceReportPath = null;

        options.optimizeArgumentsArray = true;
        options.optimizeCalls = false;
        options.optimizeParameters = false;
        options.optimizeReturns = false;

        options.prettyPrint = false;
        options.printInputDelimiter = false;
        options.propertyRenaming = PropertyRenamingPolicy.OFF;

        options.recordFunctionInformation = false;
        options.removeDeadCode = false;
        options.removeEmptyFunctions = false;
        options.removeTryCatchFinally = false;
        options.removeUnusedLocalVars = false;
        options.removeUnusedPrototypeProperties = false;
        options.removeUnusedPrototypePropertiesInExterns = false;
        options.removeUnusedVars = false;
        options.renamePrefix = null;
        options.reportMissingOverride = CheckLevel.OFF;
        options.reportPath = null;
        options.reportUnknownTypes = CheckLevel.ERROR;
        options.reserveRawExports = false;
        options.rewriteFunctionExpressions = false;

        options.smartNameRemoval = false;
        options.sourceMapDetailLevel = SourceMap.DetailLevel.SYMBOLS;
        options.sourceMapFormat = SourceMap.Format.DEFAULT;
        options.sourceMapOutputPath = null;
        options.specializeInitialModule = false;
        options.strictMessageReplacement = false;
        options.stripNamePrefixes = Collections.emptySet();
        options.stripNameSuffixes = Collections.emptySet();
        options.stripTypePrefixes = Collections.emptySet();
        options.stripTypes = Collections.emptySet();
        options.syntheticBlockEndMarker = null;
        options.syntheticBlockStartMarker = null;

        options.setAcceptConstKeyword(false);
        options.setChainCalls(true);
        options.setCodingConvention(new ClosureCodingConvention());
        options.setCollapsePropertiesOnExternTypes(false);
        options.setColorizeErrorOutput(true);
        options.setLooseTypes(false);
        options.setManageClosureDependencies(false);
        options.setNameAnonymousFunctionsOnly(false);
        options.setOutputCharset("UTF-8");
        options.setProcessObjectPropertyString(false);
        options.setRemoveAbstractMethods(true);
        options.setRemoveClosureAsserts(true);
        options.setRewriteNewDateGoogNow(false);
        options.setSummaryDetailLevel(3);
        options.setTweakProcessing(TweakProcessing.OFF);
        options.setWarningLevel(DiagnosticGroups.ACCESS_CONTROLS,
            CheckLevel.ERROR);
        options.setWarningLevel(DiagnosticGroups.AMBIGUOUS_FUNCTION_DECL,
            CheckLevel.ERROR);
        options
            .setWarningLevel(DiagnosticGroups.CHECK_REGEXP, CheckLevel.ERROR);

        options.setWarningLevel(DiagnosticGroups.CHECK_TYPES, CheckLevel.ERROR);
        options.setWarningLevel(DiagnosticGroups.CHECK_USELESS_CODE,
            CheckLevel.WARNING);
        options.setWarningLevel(DiagnosticGroups.CHECK_VARIABLES,
            CheckLevel.ERROR);
        options.setWarningLevel(DiagnosticGroups.CONSTANT_PROPERTY,
            CheckLevel.ERROR);
        options
            .setWarningLevel(DiagnosticGroups.DEPRECATED, CheckLevel.WARNING);
        options.setWarningLevel(DiagnosticGroups.EXTERNS_VALIDATION,
            CheckLevel.OFF);
        options.setWarningLevel(DiagnosticGroups.FILEOVERVIEW_JSDOC,
            CheckLevel.WARNING);
        options.setWarningLevel(DiagnosticGroups.INTERNET_EXPLORER_CHECKS,
            CheckLevel.WARNING);
        options.setWarningLevel(DiagnosticGroups.INVALID_CASTS,
            CheckLevel.ERROR);
        options.setWarningLevel(DiagnosticGroups.MISSING_PROPERTIES,
            CheckLevel.ERROR);
        options.setWarningLevel(DiagnosticGroups.NON_STANDARD_JSDOC,
            CheckLevel.WARNING);
        options.setWarningLevel(DiagnosticGroups.STRICT_MODULE_DEP_CHECK,
            CheckLevel.ERROR);
        options.setWarningLevel(DiagnosticGroups.TWEAKS, CheckLevel.WARNING);
        options.setWarningLevel(DiagnosticGroups.UNDEFINED_VARIABLES,
            CheckLevel.ERROR);
        options.setWarningLevel(DiagnosticGroups.UNKNOWN_DEFINES,
            CheckLevel.WARNING);
        options.setWarningLevel(DiagnosticGroups.VISIBILITY, CheckLevel.ERROR);

        options.tightenTypes = false;
        options.tracer = TracerMode.OFF;

        options.unaliasableGlobals = null;

        options.variableRenaming = VariableRenamingPolicy.LOCAL;

        return options;
    }

    /**
     * This method wraps the original compile method and passes the compiler
     * options to it.
     *
     * @param externs
     *            The externs.
     * @param sources
     *            The sources to compile.
     * @return The compilation result.
     */
    public Result
        compile(final List<JSSourceFile> externs,
            final List<JSSourceFile> sources)
    {
        return super.compile(externs, sources, this.options);
    }
}
