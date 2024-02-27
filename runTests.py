import os
import shutil
import subprocess

runDirectory = "run/client/"
configDirectory = runDirectory + "config/phosphophyllite/"

runMode = os.environ.get("QUARTZ_TEST_RUN_MODE")
if runMode is None:
    runMode = "Automatic"
    pass

mainConfigName = "quartz-client.json5"
mainConfig = f"""
{{
    debug: false,
    mode: "{runMode}",
}}
"""

testConfigName = "quartz-testing-client.json5"
testConfig = """
{
    Enabled: true,
    AutoRun: true,
}
"""

if __name__ == '__main__':
    print(mainConfig)
    print(testConfig)
    shutil.rmtree(runDirectory)
    os.makedirs(configDirectory)

    mainConfigFile = open(configDirectory + mainConfigName, "w")
    mainConfigFile.write(mainConfig)
    mainConfigFile.close()

    testConfigFile = open(configDirectory + testConfigName, "w")
    testConfigFile.write(testConfig)
    testConfigFile.close()

    subprocess.call(["./gradlew", ":runClient"])
    pass
