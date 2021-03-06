package javaposse.jobdsl.dsl.helpers.step

import com.google.common.base.Preconditions
import javaposse.jobdsl.dsl.Context
import javaposse.jobdsl.dsl.ContextHelper
import javaposse.jobdsl.dsl.DslContext
import javaposse.jobdsl.dsl.JobManagement
import javaposse.jobdsl.dsl.RequiresPlugin
import javaposse.jobdsl.dsl.WithXmlAction
import javaposse.jobdsl.dsl.helpers.common.DownstreamContext

import static com.google.common.base.Strings.isNullOrEmpty
import static javaposse.jobdsl.dsl.helpers.LocalRepositoryLocation.LOCAL_TO_WORKSPACE

class StepContext implements Context {
    final List<Node> stepNodes = []
    protected final JobManagement jobManagement

    StepContext(JobManagement jobManagement) {
        this.jobManagement = jobManagement
    }

    void shell(String commandStr) {
        NodeBuilder nodeBuilder = new NodeBuilder()
        stepNodes << nodeBuilder.'hudson.tasks.Shell' {
            'command' commandStr
        }
    }

    void batchFile(String commandStr) {
        NodeBuilder nodeBuilder = new NodeBuilder()
        stepNodes << nodeBuilder.'hudson.tasks.BatchFile' {
            'command' commandStr
        }
    }

    @RequiresPlugin(id = 'description-setter', minimumVersion = '1.9')
    void buildDescription(String regexp, String description = null) {
        stepNodes << new NodeBuilder().'hudson.plugins.descriptionsetter.DescriptionSetterBuilder' {
            delegate.regexp(regexp ?: '')
            delegate.description(description ?: '')
        }
    }

    void gradle(@DslContext(GradleContext) Closure gradleClosure) {
        GradleContext gradleContext = new GradleContext()
        ContextHelper.executeInContext(gradleClosure, gradleContext)

        Node gradleNode = new NodeBuilder().'hudson.plugins.gradle.Gradle' {
            description gradleContext.description
            switches gradleContext.switches.join(' ')
            tasks gradleContext.tasks.join(' ')
            rootBuildScriptDir gradleContext.rootBuildScriptDir
            buildFile gradleContext.buildFile
            gradleName gradleContext.gradleName
            useWrapper gradleContext.useWrapper
            makeExecutable gradleContext.makeExecutable
            fromRootBuildScriptDir gradleContext.fromRootBuildScriptDir
        }

        if (gradleContext.configureBlock) {
            WithXmlAction action = new WithXmlAction(gradleContext.configureBlock)
            action.execute(gradleNode)
        }

        stepNodes << gradleNode
    }

    void gradle(String tasks = null, String switches = null, Boolean useWrapper = true, Closure configure = null) {
        gradle {
            if (tasks != null) {
                delegate.tasks(tasks)
            }
            if (switches != null) {
                delegate.switches(switches)
            }
            if (useWrapper != null) {
                delegate.useWrapper(useWrapper)
            }
            delegate.configure(configure)
        }
    }

    void sbt(String sbtNameArg, String actionsArg = null, String sbtFlagsArg=null,  String jvmFlagsArg=null,
            String subdirPathArg=null, Closure configure = null) {

        NodeBuilder nodeBuilder = new NodeBuilder()

        Node sbtNode = nodeBuilder.'org.jvnet.hudson.plugins.SbtPluginBuilder' {
            name Preconditions.checkNotNull(sbtNameArg, 'Please provide the name of the SBT to use' as Object)
            jvmFlags jvmFlagsArg ?: ''
            sbtFlags sbtFlagsArg ?: ''
            actions actionsArg ?: ''
            subdirPath subdirPathArg ?: ''
        }

        // Apply Context
        if (configure) {
            WithXmlAction action = new WithXmlAction(configure)
            action.execute(sbtNode)
        }

        stepNodes << sbtNode

    }

    void dsl(@DslContext(javaposse.jobdsl.dsl.helpers.step.DslContext) Closure configure) {
        javaposse.jobdsl.dsl.helpers.step.DslContext context = new javaposse.jobdsl.dsl.helpers.step.DslContext()
        ContextHelper.executeInContext(configure, context)

        stepNodes << new NodeBuilder().'javaposse.jobdsl.plugin.ExecuteDslScripts' {
            targets context.externalScripts.join('\n')
            usingScriptText !isNullOrEmpty(context.scriptText)
            scriptText context.scriptText ?: ''
            ignoreExisting context.ignoreExisting
            removedJobAction context.removedJobAction
            additionalClasspath context.additionalClasspath ?: ''
        }
    }

    void dsl(String scriptText, String removedJobAction = null, boolean ignoreExisting = false) {
        dsl {
            text(scriptText)
            if (removedJobAction) {
                removeAction(removedJobAction)
            }
            delegate.ignoreExisting(ignoreExisting)
        }
    }

    void dsl(Iterable<String> externalScripts, String removedJobAction = null, boolean ignoreExisting = false) {
        dsl {
            external(externalScripts)
            if (removedJobAction) {
                removeAction(removedJobAction)
            }
            delegate.ignoreExisting(ignoreExisting)
        }
    }

    void ant(@DslContext(AntContext) Closure antClosure = null) {
        ant(null, null, null, antClosure)
    }

    void ant(String targetsStr, @DslContext(AntContext) Closure antClosure = null) {
        ant(targetsStr, null, null, antClosure)
    }

    void ant(String targetsStr, String buildFileStr, @DslContext(AntContext) Closure antClosure = null) {
        ant(targetsStr, buildFileStr, null, antClosure)
    }

    void ant(String targetsArg, String buildFileArg, String antInstallation,
             @DslContext(AntContext) Closure antClosure = null) {
        AntContext antContext = new AntContext()
        ContextHelper.executeInContext(antClosure, antContext)

        List<String> targetList = []

        if (targetsArg) {
            targetList.addAll targetsArg.contains('\n') ? targetsArg.split('\n') : targetsArg.split(' ')
        }
        targetList.addAll antContext.targets

        List<String> antOptsList = antContext.antOpts

        List<String> propertiesList = []
        propertiesList += antContext.props

        NodeBuilder nodeBuilder = NodeBuilder.newInstance()
        Node antNode = nodeBuilder.'hudson.tasks.Ant' {
            targets targetList.join(' ')

            antName antInstallation ?: antContext.antName ?: '(Default)'

            if (antOptsList) {
                antOpts antOptsList.join('\n')
            }

            if (buildFileArg || antContext.buildFile) {
                buildFile buildFileArg ?: antContext.buildFile
            }
        }

        if (propertiesList) {
            antNode.appendNode('properties', propertiesList.join('\n'))
        }

        stepNodes << antNode
    }

    void groovyCommand(String command, @DslContext(GroovyContext) Closure groovyClosure = null) {
        groovy(command, true, null, groovyClosure)
    }

    void groovyCommand(String command, String groovyName, @DslContext(GroovyContext) Closure groovyClosure = null) {
        groovy(command, true, groovyName, groovyClosure)
    }

    void groovyScriptFile(String fileName, @DslContext(GroovyContext) Closure groovyClosure = null) {
        groovy(fileName, false, null, groovyClosure)
    }

    void groovyScriptFile(String fileName, String groovyName, @DslContext(GroovyContext) Closure groovyClosure = null) {
        groovy(fileName, false, groovyName, groovyClosure)
    }

    protected groovyScriptSource(String commandOrFileName, boolean isCommand) {
        NodeBuilder nodeBuilder = new NodeBuilder()
        nodeBuilder.scriptSource(class: "hudson.plugins.groovy.${isCommand ? 'String' : 'File'}ScriptSource") {
            if (isCommand) {
                command commandOrFileName
            } else {
                scriptFile commandOrFileName
            }
        }
    }

    protected groovy(String commandOrFileName, boolean isCommand, String groovyInstallation, Closure groovyClosure) {
        GroovyContext groovyContext = new GroovyContext()
        ContextHelper.executeInContext(groovyClosure, groovyContext)

        Node groovyNode = NodeBuilder.newInstance().'hudson.plugins.groovy.Groovy' {
            groovyName groovyInstallation ?: groovyContext.groovyInstallation ?: '(Default)'
            parameters groovyContext.groovyParams.join('\n')
            scriptParameters groovyContext.scriptParams.join('\n')
            javaOpts groovyContext.javaOpts.join(' ')
            classPath groovyContext.classpathEntries.join(File.pathSeparator)
        }
        groovyNode.append(groovyScriptSource(commandOrFileName, isCommand))
        groovyNode.appendNode('properties', groovyContext.props.join('\n'))

        stepNodes << groovyNode
    }

    void systemGroovyCommand(String command, @DslContext(SystemGroovyContext) Closure systemGroovyClosure = null) {
        systemGroovy(command, true, systemGroovyClosure)
    }

    void systemGroovyScriptFile(String fileName, @DslContext(SystemGroovyContext) Closure systemGroovyClosure = null) {
        systemGroovy(fileName, false, systemGroovyClosure)
    }

    protected systemGroovy(String commandOrFileName, boolean isCommand, Closure systemGroovyClosure) {
        SystemGroovyContext systemGroovyContext = new SystemGroovyContext()
        ContextHelper.executeInContext(systemGroovyClosure, systemGroovyContext)

        Node systemGroovyNode = NodeBuilder.newInstance().'hudson.plugins.groovy.SystemGroovy' {
            bindings systemGroovyContext.bindings.collect { key, value -> "${key}=${value}" }.join('\n')
            classpath systemGroovyContext.classpathEntries.join(File.pathSeparator)
        }
        systemGroovyNode.append(groovyScriptSource(commandOrFileName, isCommand))

        stepNodes << systemGroovyNode
    }

    void maven(@DslContext(MavenContext) Closure closure) {
        MavenContext mavenContext = new MavenContext(jobManagement)
        ContextHelper.executeInContext(closure, mavenContext)

        Node mavenNode = new NodeBuilder().'hudson.tasks.Maven' {
            targets mavenContext.goals.join(' ')
            if (mavenContext.properties) {
                properties(mavenContext.properties.collect { key, value -> "${key}=${value}" }.join('\n'))
            }
            mavenName mavenContext.mavenInstallation
            jvmOptions mavenContext.mavenOpts.join(' ')
            if (mavenContext.rootPOM) {
                pom mavenContext.rootPOM
            }
            usePrivateRepository mavenContext.localRepositoryLocation == LOCAL_TO_WORKSPACE
            if (mavenContext.providedSettingsId) {
                settings(class: 'org.jenkinsci.plugins.configfiles.maven.job.MvnSettingsProvider') {
                    settingsConfigId(mavenContext.providedSettingsId)
                }
            }
        }

        // Apply Context
        if (mavenContext.configureBlock) {
            WithXmlAction action = new WithXmlAction(mavenContext.configureBlock)
            action.execute(mavenNode)
        }

        stepNodes << mavenNode
    }

    void maven(String targetsArg = null, String pomArg = null, Closure configure = null) {
        maven {
            delegate.goals(targetsArg)
            delegate.rootPOM(pomArg)
            delegate.configure(configure)
        }
    }

    void grails(@DslContext(GrailsContext) Closure grailsClosure) {
        grails null, false, grailsClosure
    }

    void grails(String targetsArg, @DslContext(GrailsContext) Closure grailsClosure) {
        grails targetsArg, false, grailsClosure
    }

    void grails(String targetsArg = null, boolean useWrapperArg = false,
                @DslContext(GrailsContext) Closure grailsClosure = null) {
        GrailsContext grailsContext = new GrailsContext(
            useWrapper: useWrapperArg
        )
        ContextHelper.executeInContext(grailsClosure, grailsContext)

        NodeBuilder nodeBuilder = new NodeBuilder()
        Node grailsNode = nodeBuilder.'com.g2one.hudson.grails.GrailsBuilder' {
            targets targetsArg ?: grailsContext.targetsString
            name grailsContext.name
            grailsWorkDir grailsContext.grailsWorkDir
            projectWorkDir grailsContext.projectWorkDir
            projectBaseDir grailsContext.projectBaseDir
            serverPort grailsContext.serverPort
            'properties' grailsContext.propertiesString
            forceUpgrade grailsContext.forceUpgrade.toString()
            nonInteractive grailsContext.nonInteractive.toString()
            useWrapper grailsContext.useWrapper.toString()
        }

        stepNodes << grailsNode
    }

    void copyArtifacts(String jobName, String includeGlob,
                       @DslContext(CopyArtifactContext) Closure copyArtifactClosure) {
        copyArtifacts(jobName, includeGlob, '', copyArtifactClosure)
    }

    void copyArtifacts(String jobName, String includeGlob, String targetPath,
                       @DslContext(CopyArtifactContext) Closure copyArtifactClosure) {
        copyArtifacts(jobName, includeGlob, targetPath, false, copyArtifactClosure)
    }

    void copyArtifacts(String jobName, String includeGlob, String targetPath = '', boolean flattenFiles,
                       @DslContext(CopyArtifactContext) Closure copyArtifactClosure) {
        copyArtifacts(jobName, includeGlob, targetPath, flattenFiles, false, copyArtifactClosure)
    }

    @RequiresPlugin(id = 'copyartifact', minimumVersion = '1.26')
    void copyArtifacts(String jobName, String includeGlob, String targetPath = '', boolean flattenFiles,
                       boolean optionalAllowed,
                       @DslContext(CopyArtifactContext) Closure copyArtifactClosure) {
        CopyArtifactContext copyArtifactContext = new CopyArtifactContext()
        ContextHelper.executeInContext(copyArtifactClosure, copyArtifactContext)

        if (!copyArtifactContext.selectedSelector) {
            throw new IllegalArgumentException('A selector has to be select in the closure argument')
        }

        NodeBuilder nodeBuilder = NodeBuilder.newInstance()
        Node copyArtifactNode = nodeBuilder.'hudson.plugins.copyartifact.CopyArtifact' {
            project jobName
            filter includeGlob
            target targetPath ?: ''

            selector(class: "hudson.plugins.copyartifact.${copyArtifactContext.selectedSelector}Selector") {
                if (copyArtifactContext.selectedSelector == 'TriggeredBuild' && copyArtifactContext.fallback) {
                    fallbackToLastSuccessful 'true'
                }
                if (copyArtifactContext.selectedSelector == 'StatusBuild' && copyArtifactContext.stable) {
                    stable 'true'
                }
                if (copyArtifactContext.selectedSelector == 'PermalinkBuild') {
                    id copyArtifactContext.permalinkName
                }
                if (copyArtifactContext.selectedSelector == 'SpecificBuild') {
                    buildNumber copyArtifactContext.buildNumber
                }
                if (copyArtifactContext.selectedSelector == 'ParameterizedBuild') {
                    parameterName copyArtifactContext.parameterName
                }
            }

            if (flattenFiles) {
                flatten 'true'
            }
            if (optionalAllowed) {
                optional 'true'
            }
        }

        stepNodes << copyArtifactNode

    }

    void resolveArtifacts(@DslContext(RepositoryConnectorContext) Closure repositoryConnectorClosure) {
        RepositoryConnectorContext context = new RepositoryConnectorContext()
        ContextHelper.executeInContext(repositoryConnectorClosure, context)

        stepNodes << new NodeBuilder().'org.jvnet.hudson.plugins.repositoryconnector.ArtifactResolver' {
            targetDirectory context.targetDirectory ?: ''
            failOnError context.failOnError
            enableRepoLogging context.enableRepoLogging
            snapshotUpdatePolicy context.snapshotUpdatePolicy
            releaseUpdatePolicy context.releaseUpdatePolicy
            snapshotChecksumPolicy 'warn'
            releaseChecksumPolicy 'warn'
            artifacts context.artifactNodes
        }
    }

    void prerequisite(String projectList = '', boolean warningOnlyBool = false) {
        NodeBuilder nodeBuilder = new NodeBuilder()
        Node preReqNode = nodeBuilder.'dk.hlyh.ciplugins.prereqbuildstep.PrereqBuilder' {
             // Important that there are no spaces for comma delimited values, plugin doesn't trim, so we will
            projects(projectList.tokenize(',')*.trim().join(','))
            warningOnly(warningOnlyBool)
        }
        stepNodes << preReqNode
    }

    void publishOverSsh(@DslContext(PublishOverSshContext) Closure publishOverSshClosure) {
        PublishOverSshContext publishOverSshContext = new PublishOverSshContext()
        ContextHelper.executeInContext(publishOverSshClosure, publishOverSshContext)

        Preconditions.checkArgument(!publishOverSshContext.servers.empty, 'At least 1 server must be configured')

        stepNodes << new NodeBuilder().'jenkins.plugins.publish__over__ssh.BapSshBuilderPlugin' {
            delegate.delegate {
                consolePrefix('SSH: ')
                delegate.delegate {
                    publishers {
                        publishOverSshContext.servers.each { server ->
                            'jenkins.plugins.publish__over__ssh.BapSshPublisher' {
                                configName(server.name)
                                verbose(server.verbose)
                                transfers {
                                    server.transferSets.each { transferSet ->
                                        'jenkins.plugins.publish__over__ssh.BapSshTransfer' {
                                            remoteDirectory(transferSet.remoteDirectory ?: '')
                                            sourceFiles(transferSet.sourceFiles ?: '')
                                            excludes(transferSet.excludeFiles ?: '')
                                            removePrefix(transferSet.removePrefix ?: '')
                                            remoteDirectorySDF(transferSet.remoteDirIsDateFormat)
                                            flatten(transferSet.flattenFiles)
                                            cleanRemote(false)
                                            noDefaultExcludes(transferSet.noDefaultExcludes)
                                            makeEmptyDirs(transferSet.makeEmptyDirs)
                                            patternSeparator(transferSet.patternSeparator)
                                            execCommand(transferSet.execCommand ?: '')
                                            execTimeout(transferSet.execTimeout)
                                            usePty(transferSet.execInPty)
                                        }
                                    }
                                }
                                useWorkspaceInPromotion(false)
                                usePromotionTimestamp(false)
                                if (server.retry) {
                                    retry(class: 'jenkins.plugins.publish_over_ssh.BapSshRetry') {
                                        retries(server.retries)
                                        retryDelay(server.delay)
                                    }
                                }
                                if (server.credentials) {
                                    credentials(class: 'jenkins.plugins.publish_over_ssh.BapSshCredentials') {
                                        secretPassphrase('')
                                        key(server.credentials.key ?: '')
                                        keyPath(server.credentials.pathToKey ?: '')
                                        username(server.credentials.username)
                                    }
                                }
                                if (server.label) {
                                    label(class: 'jenkins.plugins.publish_over_ssh.BapSshPublisherLabel') {
                                        label(server.label)
                                    }
                                }
                            }
                        }
                    }
                    continueOnError(publishOverSshContext.continueOnError)
                    failOnError(publishOverSshContext.failOnError)
                    alwaysPublishFromMaster(publishOverSshContext.alwaysPublishFromMaster)
                    hostConfigurationAccess(
                            class: 'jenkins.plugins.publish_over_ssh.BapSshPublisherPlugin',
                            reference: '../..',
                    )
                    if (publishOverSshContext.parameterName) {
                        paramPublish(class: 'jenkins.plugins.publish_over_ssh.BapSshParamPublish') {
                            parameterName(publishOverSshContext.parameterName)
                        }
                    }
                }
            }
        }
    }

    void downstreamParameterized(@DslContext(DownstreamContext) Closure downstreamClosure) {
        DownstreamContext downstreamContext = new DownstreamContext()
        ContextHelper.executeInContext(downstreamClosure, downstreamContext)

        Node stepNode = downstreamContext.createDownstreamNode(true)
        stepNodes << stepNode
    }

    void conditionalSteps(@DslContext(ConditionalStepsContext) Closure conditionalStepsClosure) {
        ConditionalStepsContext conditionalStepsContext = new ConditionalStepsContext(jobManagement)
        ContextHelper.executeInContext(conditionalStepsClosure, conditionalStepsContext)

        if (conditionalStepsContext.stepNodes.size() > 1) {
            stepNodes << conditionalStepsContext.createMultiStepNode()
        } else {
            stepNodes << conditionalStepsContext.createSingleStepNode()
        }
    }

    void environmentVariables(@DslContext(StepEnvironmentVariableContext) Closure envClosure) {
        StepEnvironmentVariableContext envContext = new StepEnvironmentVariableContext()
        ContextHelper.executeInContext(envClosure, envContext)

        Node envNode = new NodeBuilder().'EnvInjectBuilder' {
            envContext.addInfoToBuilder(delegate)
        }

        stepNodes << envNode
    }

    void remoteTrigger(String remoteJenkins, String jobName,
                       @DslContext(ParameterizedRemoteTriggerContext) Closure closure = null) {
        Preconditions.checkArgument(!isNullOrEmpty(remoteJenkins), 'remoteJenkins must be specified')
        Preconditions.checkArgument(!isNullOrEmpty(jobName), 'jobName must be specified')

        ParameterizedRemoteTriggerContext context = new ParameterizedRemoteTriggerContext()
        ContextHelper.executeInContext(closure, context)

        List<String> jobParameters = context.parameters.collect { String key, String value -> "$key=$value" }

        stepNodes << new NodeBuilder().'org.jenkinsci.plugins.ParameterizedRemoteTrigger.RemoteBuildConfiguration' {
            token()
            remoteJenkinsName(remoteJenkins)
            job(jobName)
            shouldNotFailBuild(context.shouldNotFailBuild)
            pollInterval(context.pollInterval)
            preventRemoteBuildQueue(context.preventRemoteBuildQueue)
            blockBuildUntilComplete(context.blockBuildUntilComplete)
            parameters(jobParameters.join('\n'))
            parameterList {
                if (jobParameters.empty) {
                    string()
                } else {
                    jobParameters.each { String value ->
                        string(value)
                    }
                }
            }
            overrideAuth(false)
            auth {
                'org.jenkinsci.plugins.ParameterizedRemoteTrigger.Auth' {
                    NONE('none')
                    API__TOKEN('apiToken')
                    CREDENTIALS__PLUGIN('credentialsPlugin')
                }
            }
            loadParamsFromFile(false)
            parameterFile()
            queryString()
        }
    }

    void criticalBlock(@DslContext(StepContext) Closure closure) {
        StepContext stepContext = new StepContext(jobManagement)
        ContextHelper.executeInContext(closure, stepContext)

        stepNodes << new NodeBuilder().'org.jvnet.hudson.plugins.exclusion.CriticalBlockStart'()
        stepNodes.addAll(stepContext.stepNodes)
        stepNodes << new NodeBuilder().'org.jvnet.hudson.plugins.exclusion.CriticalBlockEnd'()
    }

    void rake(@DslContext(RakeContext) Closure rakeClosure = null) {
        rake(null, rakeClosure)
    }

    void rake(String tasksArg, @DslContext(RakeContext) Closure rakeClosure = null) {
        RakeContext rakeContext = new RakeContext()

        if (tasksArg) {
            rakeContext.task(tasksArg)
        }

        ContextHelper.executeInContext(rakeClosure, rakeContext)

        stepNodes << new NodeBuilder().'hudson.plugins.rake.Rake' {
            rakeInstallation rakeContext.installation
            rakeFile rakeContext.file
            rakeLibDir rakeContext.libDir
            rakeWorkingDir rakeContext.workingDir
            tasks rakeContext.tasks.join(' ')
            silent rakeContext.silent
            bundleExec rakeContext.bundleExec
        }
    }

    void vSpherePowerOff(String server, String vm) {
        vSphereBuildStep(server, 'PowerOff') {
            delegate.vm vm
            evenIfSuspended false
            shutdownGracefully false
        }
    }

    void vSpherePowerOn(String server, String vm) {
        vSphereBuildStep(server, 'PowerOn') {
            delegate.vm vm
            timeoutInSeconds 180
        }
    }

    void vSphereRevertToSnapshot(String server, String vm, String snapshot) {
        vSphereBuildStep(server, 'RevertToSnapshot') {
            delegate.vm vm
            snapshotName snapshot
        }
    }

    private vSphereBuildStep(String server, String builder, Closure configuration) {
        int hash = Preconditions.checkNotNull(
                jobManagement.getVSphereCloudHash(server),
                "vSphere server ${server} does not exist"
        )
        stepNodes << new NodeBuilder().'org.jenkinsci.plugins.vsphere.VSphereBuildStepContainer' {
            buildStep(class: "org.jenkinsci.plugins.vsphere.builders.${builder}", configuration)
            serverName server
            serverHash hash
        }
    }

    void httpRequest(String requestUrl, @DslContext(HttpRequestContext) Closure closure = null) {
        HttpRequestContext context = new HttpRequestContext()
        ContextHelper.executeInContext(closure, context)

        stepNodes << new NodeBuilder().'jenkins.plugins.http__request.HttpRequest' {
            url(requestUrl)
            if (context.httpMode != null) {
                httpMode(context.httpMode)
            }
            if (context.authentication != null) {
                authentication(context.authentication)
            }
            if (context.returnCodeBuildRelevant != null) {
                returnCodeBuildRelevant(context.returnCodeBuildRelevant)
            }
            if (context.logResponseBody != null) {
                logResponseBody(context.logResponseBody)
            }
        }
    }

    void nodejsCommand(String commandScript, String installation) {
        stepNodes << new NodeBuilder().'jenkins.plugins.nodejs.NodeJsCommandInterpreter' {
            command(commandScript)
            nodeJSInstallationName(installation)
        }
    }

    @RequiresPlugin(id = 'debian-package-builder', minimumVersion = '1.6.6')
    void debianPackage(String path, @DslContext(DebianContext) Closure closure = null) {
        Preconditions.checkArgument(!isNullOrEmpty(path), 'path must be specified')

        DebianContext context = new DebianContext()
        ContextHelper.executeInContext(closure, context)

        stepNodes << new NodeBuilder().'ru.yandex.jenkins.plugins.debuilder.DebianPackageBuilder' {
            pathToDebian(path)
            nextVersion(context.nextVersion ?: '')
            generateChangelog(context.generateChangelog)
            signPackage(context.signPackage)
            buildEvenWhenThereAreNoChanges(context.alwaysBuild)
        }
    }
}
