import gzip,hashlib,importlib.util,json,pathlib,tempfile,unittest
spec=importlib.util.spec_from_file_location('analysis',pathlib.Path(__file__).parents[1]/'analyze_dataset.py');mod=importlib.util.module_from_spec(spec);spec.loader.exec_module(mod)
class AnalysisTests(unittest.TestCase):
 def fixture(self,root,label=True):
  events=[]
  def add(stream,data,sec):events.append({'schemaVersion':1,'sessionId':'fixture','seq':len(events),'wallMs':1700000000000+sec*1000,'monoNs':sec*10**9,'clockEpoch':'a','stream':stream,'data':data})
  add('session_start',{},0)
  if label:add('user_label',{'activity':'walking'},1)
  for sec in range(2,32):add('algorithm_prediction',{'candidate':'walking' if sec<20 else 'running'},sec)
  add('session_end',{},32)
  for i,chunk in enumerate([events[:17],events[17:]]):
   data=gzip.compress(('\n'.join(json.dumps(e) for e in chunk)+'\n').encode());name=f'part_{i:06d}.jsonl.gz';(root/name).write_bytes(data)
   (root/f'part_{i:06d}.meta.json').write_text(json.dumps({'part':i,'file':name,'firstSeq':chunk[0]['seq'],'lastSeq':chunk[-1]['seq'],'eventCount':len(chunk),'sha256':hashlib.sha256(data).hexdigest(),'bytes':len(data)}))
  (root/'manifest.json').write_text(json.dumps({'id':'fixture','state':'closed','partCount':2,'eventCount':len(events)}))
 def test_boundary_not_destructively_deleted(self):
  with tempfile.TemporaryDirectory() as d:
   r=pathlib.Path(d);self.fixture(r);before=(r/'part_000000.jsonl.gz').read_bytes();a=mod.analyze(r,10);self.assertEqual(a['referenceSamples'],11);self.assertEqual((r/'part_000000.jsonl.gz').read_bytes(),before)
 def test_unlabeled_has_no_accuracy(self):
  with tempfile.TemporaryDirectory() as d:r=pathlib.Path(d);self.fixture(r,False);self.assertIsNone(mod.analyze(r)['referenceAccuracy'])
 def test_checksum_corruption_rejected(self):
  with tempfile.TemporaryDirectory() as d:
   r=pathlib.Path(d);self.fixture(r);p=r/'part_000000.jsonl.gz';p.write_bytes(p.read_bytes()+b'x')
   with self.assertRaises(mod.DatasetError):mod.analyze(r)
 def test_missing_part_rejected(self):
  with tempfile.TemporaryDirectory() as d:
   r=pathlib.Path(d);self.fixture(r);(r/'part_000000.meta.json').unlink()
   with self.assertRaises(mod.DatasetError):mod.analyze(r)
if __name__=='__main__':unittest.main()
