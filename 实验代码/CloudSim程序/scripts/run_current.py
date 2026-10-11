"""Build/run the frozen current model without changing published data."""
from pathlib import Path
from concurrent.futures import ThreadPoolExecutor
import argparse
import datetime
import hashlib
import json
import os
import shutil
import subprocess

HERE=Path(__file__).resolve().parents[1]
ROOT=HERE.parents[1]
MAIN='com.edcbo.research.claim.ClaimPreservingCloudSimExperiment'
EXPECTED='a52444877768a42129eb9dcd637f9a3d18f25eb94a8441e21b541ff773854b15'

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--phase',choices=['build','smoke','primary','analytic','direct'],default='smoke')
    p.add_argument('--runtime',default='D:/CodexRuntime/runs/lscbo-current' if os.name=='nt' else None)
    p.add_argument('--classpath-file',type=Path,help='UTF-8 file containing dependency JARs separated by the platform path separator')
    p.add_argument('--java-home',type=Path,default=Path(os.environ['JAVA_HOME']) if 'JAVA_HOME' in os.environ else None)
    p.add_argument('--seed-range',default='43-72')
    p.add_argument('--run-name',default=datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ'))
    args=p.parse_args()
    if not args.runtime:p.error('--runtime is required on non-Windows systems')
    runtime=Path(args.runtime).resolve();runtime.mkdir(parents=True,exist_ok=True)
    tmp=runtime/'temp';tmp.mkdir(exist_ok=True)
    os.environ.update(TEMP=str(tmp),TMP=str(tmp))
    suffix='.exe' if os.name=='nt' else ''
    def executable(name):
        if args.java_home:return str(args.java_home/'bin'/(name+suffix))
        found=shutil.which(name)
        if not found:p.error('JDK 21 is required; set JAVA_HOME or --java-home')
        return found
    java,javac=executable('java'),executable('javac')
    cpfile=args.classpath_file
    if cpfile is None:
        mvn=shutil.which('mvn')
        if not mvn:p.error('Install Maven or provide --classpath-file for existing dependency JARs')
        cpfile=runtime/'classpath.txt'
        cache=Path('D:/CodexRuntime/cache/maven') if os.name=='nt' else runtime/'cache/maven'
        subprocess.run([mvn,'-f',str(HERE/'pom.xml'),'dependency:build-classpath',
                        '-Dmaven.repo.local='+str(cache),'-Dmdep.outputFile='+str(cpfile)],check=True)
    dependencies=cpfile.read_text(encoding='utf-8-sig').strip()
    if not dependencies:p.error('Empty dependency classpath')
    for jar in dependencies.split(os.pathsep):
        if not Path(jar).is_file():p.error('Dependency JAR missing: '+jar)
    source=HERE/'src/main/java/com/edcbo/research/claim/ClaimPreservingCloudSimExperiment.java'
    if hashlib.sha256(source.read_bytes()).hexdigest()!=EXPECTED:
        raise ValueError('Frozen experiment source differs from the published run')
    classes=runtime/'classes';classes.mkdir(exist_ok=True)
    subprocess.run([javac,'-encoding','UTF-8','-cp',dependencies,'-d',str(classes),str(source)],check=True)
    print('Frozen source compiled:',EXPECTED,flush=True)
    if args.phase=='build':return
    run=runtime/args.run_name
    if run.exists():p.error('Run output already exists: choose a new --run-name')
    run.mkdir(parents=True)
    diagnostic=runtime/'classes-instrumented'
    if args.phase=='direct':
        text=source.read_text(encoding='utf-8')
        replacements=[
          ('    private static OptimizationResult optimize(','    static OptimizationResult optimize('),
          ('    private static LocalSearchResult localSearch(','    static LocalSearchResult localSearch('),
          ('    static Metrics evaluateRaw(int[] schedule, Scenario scenario, WeightProfile weights, Norm norm) {',
           '    static Metrics evaluateRaw(int[] schedule, Scenario scenario, WeightProfile weights, Norm norm) {\n        ColdAudit coldAudit = COLD_AUDIT.get();\n        if (coldAudit != null) coldAudit.scoreCalls++;'),
          ('        int[] best = startingSchedule.clone();','        int[] best = startingSchedule.clone();\n        ColdAudit coldAudit = COLD_AUDIT.get();\n        if (coldAudit != null) coldAudit.localStart = startingSchedule.clone();'),
          ('            if (roundBest != null) {','            if (roundBest != null) {\n                if (coldAudit != null) coldAudit.acceptedExchanges++;'),
          ('    static final class LocalSearchResult {','    static final ThreadLocal<ColdAudit> COLD_AUDIT = new ThreadLocal<>();\n    static final class ColdAudit {\n        long scoreCalls;\n        int acceptedExchanges;\n        int[] localStart;\n    }\n\n    static final class LocalSearchResult {')]
        for old,new in replacements:
            if text.count(old)!=1:raise ValueError('Instrumentation anchor mismatch')
            text=text.replace(old,new)
        src=runtime/'instrumented-src';src.mkdir(exist_ok=True)
        instrument=src/source.name;instrument.write_text(text,encoding='utf-8',newline='\n')
        expected_instrument='df65fe9fa36443e77086c67afa7d677e198275c284667833cf3d5196d12181d5'
        if hashlib.sha256(instrument.read_bytes()).hexdigest()!=expected_instrument:
            raise ValueError('Instrumented source differs from the published diagnostic build')
        diagnostic.mkdir(exist_ok=True)
        subprocess.run([javac,'-encoding','UTF-8','-cp',dependencies,'-d',str(diagnostic),
                        str(instrument),str(HERE/'diagnostics/ColdDirectPairStudy.java')],check=True)
    def job(name,params,diagnostics=False):
        out=run/name;out.mkdir()
        cmd=[java,'-Xms512m','-Xmx2g','-XX:+UseSerialGC','-Djava.io.tmpdir='+str(tmp),
             '-Dcost.model=batch_reserved_seconds','-cp',str(diagnostic if diagnostics else classes)+os.pathsep+dependencies,*params]
        if not diagnostics:cmd+=['--out',str(out)]
        print('Running',name,'→',out,flush=True)
        with (out/'run.log').open('w',encoding='utf-8') as log:
            subprocess.run(cmd,stdout=log,stderr=subprocess.STDOUT,check=True)
        print('Completed',name,flush=True)
    if args.phase=='smoke':
        job('smoke',[MAIN,'--mode','smoke','--cloudsim','true','--parallelism','1'])
    elif args.phase=='primary':
        job('primary',[MAIN,'--mode','mainfull','--seedRange',args.seed_range,'--cloudsim','false','--parallelism','8'])
    elif args.phase=='analytic':
        jobs=[('main',[MAIN,'--mode','mainextended','--seedRange',args.seed_range,'--cloudsim','false','--parallelism','8']),
              ('sensitivity',[MAIN,'--mode','formalfull','--seedRange',args.seed_range,'--experiments','weight_sensitivity,vm_heterogeneity','--cloudsim','false','--parallelism','6'])]
        with ThreadPoolExecutor(max_workers=2) as pool:
            futures=[pool.submit(job,name,params) for name,params in jobs]
            for future in futures:future.result()
    elif args.phase=='direct':
        baseline=ROOT/'论文/Cost与云调度两个板块/数据/冷启动_3990行.csv'
        job('direct',['com.edcbo.research.claim.ColdDirectPairStudy',str(baseline),str(run/'direct'),'5'],True)
    (run/'run_manifest.json').write_text(json.dumps({'source_sha256':EXPECTED,'cost_model':'batch_reserved_seconds',
        'phase':args.phase,'seed_range':args.seed_range,'runtime':str(runtime),'published_data_untouched':True},indent=2),encoding='utf-8')
    print('Outputs:',run,flush=True)

if __name__=='__main__':main()
