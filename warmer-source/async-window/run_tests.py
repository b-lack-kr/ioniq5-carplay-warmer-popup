from pathlib import Path
import subprocess
import tempfile

here = Path(__file__).resolve().parent
root = here.parents[4]
java = root / '.tools/java-build/jdk-17.0.21+9/Contents/Home/bin'
with tempfile.TemporaryDirectory(prefix='warmer-async-') as out:
    subprocess.run([str(java / 'javac'), '--release', '8', '-d', out,
                    str(here.parent / 'src/kr/ioniq5/warmerasync/AsyncOpenCoordinator.java'),
                    str(here / 'AsyncOpenCoordinatorTest.java')], check=True)
    subprocess.run([str(java / 'java'), '-cp', out,
                    'kr.ioniq5.warmerasync.AsyncOpenCoordinatorTest'], check=True)
