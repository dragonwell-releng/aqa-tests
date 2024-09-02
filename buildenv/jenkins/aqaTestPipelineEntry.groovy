#!groovy

def PLATFORMS = params.PLATFORMS.trim().split("\\s*,\\s*");
def CUSTOMIZED_SDK_URLS = params.CUSTOMIZED_SDK_URL.trim().split("\\s*,\\s*")
def UPSTREAM_JOB_NAMES = params.UPSTREAM_JOB_NAME.trim().split("\\s*,\\s*")
def UPSTREAM_JOB_NUMBERS = params.UPSTREAM_JOB_NUMBER.trim().split("\\s*,\\s*")

def USE_TESTENV_PROPERTIES = params.USE_TESTENV_PROPERTIES ? params.USE_TESTENV_PROPERTIES : false
def PARALLEL = params.PARALLEL ? params.PARALLEL : "Dynamic"
def NUM_MACHINES = params.NUM_MACHINES ? params.NUM_MACHINES : 3
def SDK_RESOURCE = params.SDK_RESOURCE ? params.SDK_RESOURCE : "releases"
def TIME_LIMIT = params.TIME_LIMIT ? params.TIME_LIMIT : 10
def AUTO_AQA_GEN = params.AUTO_AQA_GEN ? params.AUTO_AQA_GEN : false
def TRSS_URL = params.TRSS_URL ? params.TRSS_URL : "https://trss.adoptium.net/"
def LABEL = (params.LABEL) ?: ""
def LABEL_ADDITION = (params.LABEL_ADDITION) ?: ""
def TEST_FLAG = (params.TEST_FLAG) ?: ""
def APPLICATION_OPTIONS = (params.APPLICATION_OPTIONS) ?: ""
def SETUP_JCK_RUN = params.SETUP_JCK_RUN ?: false


// Use BUILD_USER_ID if set and jdk-JDK_VERSIONS
def DEFAULT_SUFFIX = (env.BUILD_USER_ID) ? "${env.BUILD_USER_ID} - jdk-${params.JDK_VERSIONS}" : "jdk-${params.JDK_VERSIONS}"
def PIPELINE_DISPLAY_NAME = (params.PIPELINE_DISPLAY_NAME) ? "#${currentBuild.number} - ${params.PIPELINE_DISPLAY_NAME}" : "#${currentBuild.number} - ${DEFAULT_SUFFIX}"

def JOBS = [:]

if (!params.TEST_JOB_NAME)
  error 'TEST_JOB_NAME is null'

// Set the AQA_TEST_PIPELINE Jenkins job displayName
currentBuild.setDisplayName(PIPELINE_DISPLAY_NAME)

if (params.SDK_RESOURCE.trim() == 'customized' && PLATFORMS.size() != CUSTOMIZED_SDK_URLS.size())
  error 'the size of PLATFORMS should be the same as the size of CUSTOMIZED_SDK_URLS'

if (params.SDK_RESOURCE.trim() == 'upstream' && (PLATFORMS.size() != UPSTREAM_JOB_NAMES.size() || PLATFORMS.size() != UPSTREAM_JOB_NUMBERS.size()))
  error 'the size of PLATFORMS should be the same as the size of UPSTREAM_JOB_NAMES/UPSTREAM_JOB_NUMBERS'

for (i in 0..(PLATFORMS.size() - 1)) {
    def PLATFORM = PLATFORMS[i]
    def CUSTOMIZED_SDK_URL = params.SDK_RESOURCE.trim() == 'customized' ? CUSTOMIZED_SDK_URLS[i] : ''
    def UPSTREAM_JOB_NAME = params.SDK_RESOURCE.trim() == 'upstream' ? UPSTREAM_JOB_NAMES[i] : ''
    def UPSTREAM_JOB_NUMBER = params.SDK_RESOURCE.trim() == 'upstream' ? UPSTREAM_JOB_NUMBERS[i] : ''
    def JOB_NAME = "${TEST_JOB_NAME}-${PLATFORM}"
    def parameters = [
        string(name: 'ADOPTOPENJDK_REPO', value: params.ADOPTOPENJDK_REPO),
        string(name: 'ADOPTOPENJDK_BRANCH', value: params.ADOPTOPENJDK_BRANCH),
        string(name: 'PLATFORMS', value: PLATFORM),
        string(name: 'TARGETS', value: params.TARGETS),
        string(name: 'JDK_VERSIONS', value: params.JDK_VERSIONS),
        string(name: 'SDK_RESOURCE', value: SDK_RESOURCE),
        string(name: 'TOP_LEVEL_SDK_URL', value: params.TOP_LEVEL_SDK_URL),
        string(name: 'CUSTOMIZED_SDK_URL', value: CUSTOMIZED_SDK_URL),
        string(name: 'UPSTREAM_JOB_NAME', value: UPSTREAM_JOB_NAME),
        string(name: 'UPSTREAM_JOB_NUMBER', value: UPSTREAM_JOB_NUMBER),
        string(name: 'VARIANT', value: params.VARIANT),
        string(name: 'JVM_OPTIONS', value: params.JVM_OPTIONS),
        string(name: 'JDK_REPO', value: params.JDK_REPO),
        string(name: 'JDK_BRANCH', value: params.JDK_BRANCH),
        string(name: 'PARALLEL', value: 'None'),
        booleanParam(name: 'KEEP_WORKSPACE', value: params.KEEP_WORKSPACE),
        booleanParam(name: 'LATEST_TESTSUITE', value: params.LATEST_TESTSUITE)
    ]
    JOBS["${JOB_NAME}"] = {
        def downstreamJob = build job: params.TEST_JOB_NAME, propagate: true, parameters: parameters, wait: true
        node("worker || (ci.role.test&&hw.arch.x86&&sw.os.linux)") {
            try {
                timeout(time: 2, unit: 'HOURS') {
                    copyArtifacts(
                        projectName: TEST_JOB_NAME,
                        selector:specific("${downstreamJob.getNumber()}"),
                        filter: "**/*.tap",
                        fingerprintArtifacts: true,
                        flatten: true
                    )
                }
            } catch (Exception e) {
                echo "Cannot run copyArtifacts from job ${JOB_NAME}. Skipping copyArtifacts..."
            }
            try {
                timeout(time: 1, unit: 'HOURS') {
                    archiveArtifacts artifacts: "*.tap", fingerprint: true
                }
            } catch (Exception e) {
                echo "Cannot archiveArtifacts from job ${JOB_NAME}. "
            }
        }
    }
}
parallel JOBS
