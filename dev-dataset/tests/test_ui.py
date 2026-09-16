"""UI regression against the fully materialized WebView assets, with native bridge stubs."""
from pathlib import Path
import unittest, os, shutil, re
from playwright.sync_api import sync_playwright
ROOT=Path(__file__).resolve().parents[2]
class DatasetUiTests(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  cls.pw=sync_playwright().start()
  cls.browser=cls.pw.chromium.launch(executable_path=os.environ.get('CHROMIUM_PATH') or shutil.which('chromium') or shutil.which('google-chrome'),headless=True,args=['--no-sandbox'])
 @classmethod
 def tearDownClass(cls): cls.browser.close();cls.pw.stop()
 def setUp(self):
  self.page=self.browser.new_page(viewport={'width':412,'height':900})
  self.errors=[];self.page.on('pageerror',lambda e:self.errors.append(str(e)))
  self.page.route('http://**/*',lambda r:r.abort());self.page.route('https://**/*',lambda r:r.abort())
  self.page.evaluate('''window.dev=false;window.calls=[];window.ds={developer:true,paired:true,active:false,automatic:false,manualCapture:true,sessions:[]};
   window.YamoneSystemSettings={isDeveloperMode:()=>window.dev,setDeveloperMode:(v)=>{window.dev=v;return v;}};
   window.YamoneDataset={getState:()=>JSON.stringify(window.ds),startAutomatic:()=>{window.calls.push('start');window.ds.active=true;window.ds.automatic=true;},stopAutomatic:()=>{window.calls.push('stop');window.ds.active=false;window.ds.automatic=false;},setManualCapture:(v)=>{window.calls.push('manual');window.ds.manualCapture=v;},retry:()=>window.calls.push('retry'),mark:(...args)=>window.calls.push(args),event:(...args)=>window.calls.push(args)};
  ''')
  self.page.set_default_timeout(5000)
  assets=ROOT/'app/src/main/assets/yamone-v23'
  html=(assets/'index.html').read_text()
  html=re.sub(r'<script[^>]*src="([^"]+)"[^>]*></script>',lambda m:'<script>'+ (assets/m[1]).read_text().replace('</script>','<\\/script>') +'</script>',html)
  html=re.sub(r'<link[^>]*href="([^"]+\.css)"[^>]*>',lambda m:'<style>'+ (assets/m[1]).read_text() +'</style>',html)
  self.page.set_content(html,wait_until='load')
  self.page.evaluate("view='main';tab='settings';render();")
 def tearDown(self):self.page.close()
 def open_upload(self):
  self.page.evaluate("window.dev=true;view='main';tab='settings';render();")
  self.page.locator('[data-setting="업로드"]').click()
 def test_general_hides_upload(self):
  self.assertEqual(self.page.locator('[data-setting="업로드"]').count(),0)
 def test_developer_entry_toggle_and_manual_independence(self):
  self.open_upload();self.page.locator('#datasetToggle').click()
  self.page.evaluate('window.yamoneRenderDataset()')
  self.assertEqual(self.page.locator('#datasetToggle').inner_text(),'ON')
  self.page.locator('#datasetManual').click()
  self.assertTrue(self.page.evaluate('window.ds.automatic'))
  self.page.locator('#datasetToggle').click()
  self.assertEqual(self.page.evaluate('window.calls'),['start','manual','stop'])
 def test_reference_label_and_return_to_general(self):
  self.open_upload();self.page.locator('#datasetToggle').click();self.page.evaluate('window.yamoneRenderDataset()')
  self.page.locator('[data-dataset-label="walking"]').click()
  self.assertEqual(self.page.evaluate('window.calls[1][0]'),'walking')
  self.page.evaluate('window.dev=false;window.yamoneRenderDataset()')
  self.assertEqual(self.page.locator('#datasetToggle').count(),0)
 def test_back_preserves_settings(self):
  self.open_upload();self.page.locator('#settingBack').click()
  self.assertEqual(self.page.evaluate('view'),'main');self.assertEqual(self.page.evaluate('tab'),'settings')
if __name__=='__main__':unittest.main()
