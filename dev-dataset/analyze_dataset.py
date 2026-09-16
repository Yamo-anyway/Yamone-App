#!/usr/bin/env python3
"""Validate a Yamone diagnostic export and report reference-only accuracy.

Usage: python3 analyze_dataset.py SESSION_DIRECTORY [--boundary-seconds 10] [--output report.json]
No network access, no raw-data modifications, and no inferred labels from predictions.
"""
from __future__ import annotations
import argparse, collections, gzip, hashlib, json, pathlib, statistics
from typing import Iterator

class DatasetError(ValueError):
    pass

def events(root: pathlib.Path) -> Iterator[dict]:
    manifest=json.loads((root/'manifest.json').read_text(encoding='utf-8'))
    descriptors=sorted(root.glob('part_*.meta.json'))
    expected_seq=0
    for i,path in enumerate(descriptors):
        part=json.loads(path.read_text(encoding='utf-8'))
        if part['part'] != i: raise DatasetError('Missing or reordered part number')
        name=part['file']
        if pathlib.Path(name).name!=name or not name.endswith('.jsonl.gz'): raise DatasetError('Unsafe part path')
        raw=root/name
        digest=hashlib.sha256()
        with raw.open('rb') as f:
            for buf in iter(lambda:f.read(65536),b''): digest.update(buf)
        if digest.hexdigest()!=part['sha256']: raise DatasetError(f'Checksum mismatch: {name}')
        if raw.stat().st_size!=part['bytes']: raise DatasetError(f'Byte count mismatch: {name}')
        count=0
        if part['firstSeq']!=expected_seq:raise DatasetError('Event sequence gap at part boundary')
        with gzip.open(raw,'rt',encoding='utf-8') as f:
            for line in f:
                e=json.loads(line)
                if e.get('sessionId')!=manifest['id'] or e.get('schemaVersion')!=1: raise DatasetError('Mixed session or schema')
                if e.get('seq')!=expected_seq:raise DatasetError('Duplicated/missing event')
                expected_seq+=1;count+=1;yield e
        if count!=part['eventCount'] or part['lastSeq']!=expected_seq-1:raise DatasetError('Part event count mismatch')
    if manifest.get('state')=='closed' and (manifest.get('partCount')!=len(descriptors) or manifest.get('eventCount')!=expected_seq):
        raise DatasetError('Incomplete closed session')

def analyze(root: pathlib.Path, boundary_seconds: float=10.0) -> dict:
    if boundary_seconds<0:raise DatasetError('Boundary exclusion must be nonnegative')
    manifest=json.loads((root/'manifest.json').read_text(encoding='utf-8'))
    streams=collections.Counter(); sensor_stats={}; predictions=[]; marks=[]; records={}; refs=[]; issues=collections.Counter()
    last_wall=0;last_mono=0
    for e in events(root):
        stream=e['stream'];streams[stream]+=1;d=e['data'];mono=e['monoNs'];wall=e['wallMs'];epoch=e.get('clockEpoch','initial')
        last_wall=max(last_wall,wall);last_mono=max(last_mono,mono)
        if stream=='sensor':
            key=str(d['type']);t=d['sampleMonoNs'];stat=sensor_stats.setdefault(key,{'count':0,'first':t,'last':t,'maxGapMs':0.0,'outOfOrder':0})
            if t<stat['last']:stat['outOfOrder']+=1
            else:stat['maxGapMs']=max(stat['maxGapMs'],(t-stat['last'])/1e6)
            stat['count']+=1;stat['last']=max(stat['last'],t)
        elif stream=='algorithm_prediction':predictions.append((mono,epoch,d.get('candidate','none')))
        elif stream=='user_label':marks.append((mono,epoch,d.get('activity','unknown')))
        elif stream=='record_start':records[d['recordId']]=(mono,epoch,d)
        elif stream=='record_end':
            start=records.pop(d.get('recordId'),None)
            if start and not d.get('summary',{}).get('cancelled',False):
                t,ep,initial=start
                if not initial.get('startedByAutoDetect') and initial.get('requestedType') in ('walking','running','cycling'):
                    refs.append((t,mono,ep,initial['requestedType'],'manual_activity_selection'))
        if stream in ('clock_change','process_restart','session_end'):
            if stream!='session_end':issues[stream]+=1
        if stream=='recorder_fix':issues['gps_decision:'+str(d.get('decision'))]+=1
        if stream=='health':issues['droppedEvents']=max(issues['droppedEvents'],d.get('droppedEvents',0))
    # A user annotation is ground truth only until the next annotation or observed session end.
    marks.sort()
    for i,(start,epoch,label) in enumerate(marks):
        end=marks[i+1][0] if i+1<len(marks) and marks[i+1][1]==epoch else last_mono
        if label!='unknown':refs.append((start,end,epoch,label,'user_confirmed'))
    margin=int(boundary_seconds*1e9);confusion=collections.Counter();coverage=collections.Counter();correct=0
    for t,ep,prediction in predictions:
        matching=[r for r in refs if r[2]==ep and r[0]+margin<=t<r[1]-margin]
        matching.sort(key=lambda r:r[4]=='user_confirmed',reverse=True)
        if not matching:coverage['unlabeled_or_boundary']+=1;continue
        ref=matching[0];truth=ref[3];coverage[ref[4]]+=1;confusion[(truth,prediction)]+=1;correct+=truth==prediction
    labeled=sum(confusion.values())
    sensors={k:{'samples':s['count'],'observedHz':round((s['count']-1)/((s['last']-s['first'])/1e9),3) if s['last']>s['first'] else None,'maxGapMs':round(s['maxGapMs'],3),'outOfOrder':s['outOfOrder']} for k,s in sensor_stats.items()}
    return {'sessionId':manifest['id'],'schemaVersion':1,'appVersion':manifest.get('appVersion'),'sourceCommit':manifest.get('sourceCommit'),
        'captureMode':manifest.get('mode'),'streams':dict(streams),'sensors':sensors,'issues':dict(issues),'referenceCoverage':dict(coverage),
        'referenceSamples':labeled,'referenceAccuracy':correct/labeled if labeled else None,
        'confusion':[{'actual':a,'predicted':p,'samples':n} for (a,p),n in sorted(confusion.items())],
        'boundaryExclusionSeconds':boundary_seconds,'rawDataModified':False,
        'notes':['Unlabeled automatic segments are not a source of ground truth.',
                 'none means no confirmed activity, not a verified stationary label.',
                 'Accuracy is for these labeled intervals only, not for all people or devices.',
                 'Compare algorithms on held-out sessions/dates; never split adjacent windows into train and test.',
                 'Requested sensor rates and callback/upload schedules are not delivery guarantees.']}

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('directory',type=pathlib.Path);p.add_argument('--boundary-seconds',type=float,default=10);p.add_argument('--output',type=pathlib.Path)
    args=p.parse_args()
    try:report=analyze(args.directory,args.boundary_seconds)
    except (OSError,ValueError,KeyError) as e:p.exit(1,f'Dataset validation failed: {e}\n')
    result=json.dumps(report,ensure_ascii=False,indent=2)
    if args.output:args.output.write_text(result+'\n',encoding='utf-8')
    print(result)
if __name__=='__main__':main()
