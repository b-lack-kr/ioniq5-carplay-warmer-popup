from pathlib import Path
import subprocess
import tempfile

here = Path(__file__).resolve().parent
root = here.parents[3]
jdk = root / '.tools/java-build/jdk-17.0.21+9/Contents/Home/bin'
src = here / 'src/kr/ioniq5'
sources = [src / 'warmerhook/WarmerHook.java', src / 'warmerhook/CallbackForwarder.java', src / 'warmerhook/CacheBootstrap.java', src / 'warmerhook/AndroidCacheSource.java', src / 'warmerhook/AsyncCacheProbe.java',
           src / 'warmeripc/SerialIpcDispatcher.java',src / 'warmeripc/BoundedSnapshotCache.java',src / 'warmeripc/SingleDaemonWorker.java',src / 'warmerui/CallbackRegistry.java',src / 'warmerhook/CachePrewarm.java']
tests = [p for p in (here / 'tests').rglob('*.java') if p.name not in ('TestAsyncOverlay.java', 'AsyncUiOpenerTest.java', 'CandidateControllerTest.java', 'CandidateBackendTest.java', 'AsyncCandidateBackendTest.java', 'AsyncRecoveryRaceTest.java', 'AndroidDipoSessionTest.java', 'GateReasonTest.java')]
with tempfile.TemporaryDirectory(prefix='warmer-real-wiring-') as out:
    subprocess.run([str(jdk/'javac'), '--release', '8', '-encoding', 'UTF-8', '-d', out,
                    *map(str, sources), *map(str, tests)], check=True)
    subprocess.run([str(jdk/'java'), '-cp', out, 'kr.ioniq5.warmerhook.WarmerWiringTest'], check=True)
    subprocess.run([str(jdk/'java'), '-cp', out, 'kr.ioniq5.warmerhook.CacheBootstrapTest'], check=True)
    subprocess.run([str(jdk/'java'), '-cp', out, 'kr.ioniq5.warmerhook.AndroidCacheSourceTest'], check=True)
    subprocess.run([str(jdk/'java'), '-cp', out, 'kr.ioniq5.warmerhook.AsyncCacheProbeTest'], check=True)
    subprocess.run([str(jdk/'java'), '-cp', out, 'kr.ioniq5.warmerhook.CachePrewarmTest'], check=True)
    subprocess.run([str(jdk/'java'), '-cp', out, 'kr.ioniq5.warmeripc.SerialIpcDispatcherTest'], check=True)
    subprocess.run([str(jdk/'java'), '-cp', out, 'kr.ioniq5.warmeripc.SerialIpcBlockedTest'], check=True)
    subprocess.run([str(jdk/'java'), '-cp', out, 'kr.ioniq5.warmeripc.BoundedSnapshotCacheTest'], check=True)
