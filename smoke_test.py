import os, tempfile, json
from outputs import package_outputs
from research_engine import rule_based_analysis, normalize_work

sample = {
 'query':'Machine learning for steelmaking energy optimization', 'year_from':2020, 'year_to':2026,
 'papers':[
  {'id':'https://openalex.org/W1','title':'Machine learning prediction of BOF endpoint temperature','year':2024,'doi':'10.1234/example.1','authors':['A. Researcher','B. Scientist'],'authors_text':'A. Researcher; B. Scientist','journal':'Journal of Steel Research','citations':12,'oa_status':'gold','oa_url':'https://example.org/paper1','abstract':'This study developed a random forest model for prediction of endpoint temperature in basic oxygen furnace. The model was trained on 1200 heats. Results showed RMSE of 8.5 °C and R2 of 0.91. The study notes limitations due to plant-specific data.','is_oa':True},
  {'id':'https://openalex.org/W2','title':'Energy efficiency and process optimization in electric arc furnace','year':2023,'doi':'10.1234/example.2','authors':['C. Engineer'],'authors_text':'C. Engineer','journal':'Metallurgical Engineering Letters','citations':21,'oa_status':'green','oa_url':'https://example.org/paper2','abstract':'This experimental and statistical analysis evaluated energy consumption in electric arc furnace steelmaking. Results indicated energy demand declined by 7.2% after process optimization. Further research is needed for different scrap mixes.','is_oa':True}
 ],
 'documents':[
  {'status':'full_text','source_url':'https://example.org/paper1.pdf','format':'pdf','text':'','pages':[],'tables':[],'reason':'test'},
  {'status':'abstract_only','source_url':'','format':'abstract','text':'','pages':[],'tables':[],'reason':'test'}
 ],
 'analyses':[
  {'paper_id':'https://openalex.org/W1','evidence_level':'full_text','problem':'پیش‌بینی دمای انتهای کنورتور','objective':'پیش‌بینی دمای پایان دمش','methods':[{'category':'یادگیری ماشین','signals':['random forest']}],'materials':['steel'],'process_conditions':['1200 heats'],'input_variables':['plant process data'],'output_variables':['endpoint temperature'],'metrics':['RMSE','R2'],'quantitative_findings':['RMSE 8.5 °C','R2 0.91'],'findings':['Results showed RMSE of 8.5 °C and R2 of 0.91.'],'limitations':['plant-specific data'],'future_work':[],'evidence_quotes':[{'claim':'RMSE reported','quote':'Results showed RMSE of 8.5 °C and R2 of 0.91.','page':4}]},
  {'paper_id':'https://openalex.org/W2','evidence_level':'abstract_only','problem':'مصرف انرژی کوره قوس','objective':'کاهش انرژی مصرفی','methods':[{'category':'تحلیل آماری','signals':['statistical analysis']}],'materials':['scrap'],'process_conditions':['electric arc furnace'],'input_variables':[],'output_variables':['energy consumption'],'metrics':['energy demand'],'quantitative_findings':['7.2%'],'findings':['Results indicated energy demand declined by 7.2% after process optimization.'],'limitations':['Further research is needed for different scrap mixes.'],'future_work':['Further research is needed for different scrap mixes.'],'evidence_quotes':[{'claim':'energy demand reduction','quote':'Results indicated energy demand declined by 7.2% after process optimization.','page':None}]}
 ],
 'synthesis':{
  'abstract':'این مرور آزمایشی دو مطالعه درباره بهینه‌سازی فولادسازی را بررسی می‌کند.','keywords':['فولادسازی','یادگیری ماشین','مصرف انرژی'],
  'background':'یادگیری ماشین می‌تواند به پیش‌بینی متغیرهای فرایندی کمک کند، اما تعمیم مدل‌ها نیازمند بررسی داده‌های چند کارخانه است.',
  'thematic_synthesis':'مطالعات به دو جریان پیش‌بینی پارامتر و بهینه‌سازی انرژی تقسیم می‌شوند.',
  'methods_comparison':['مطالعه اول جنگل تصادفی برای پیش‌بینی؛ مطالعه دوم تحلیل آماری روی داده‌های فرایندی.'],
  'results_comparison':['RMSE مطالعه اول 8.5 درجه گزارش شده و مطالعه دوم کاهش انرژی 7.2 درصدی گزارش کرده است؛ این دو خروجی قابل مقایسه مستقیم نیستند.'],
  'convergent_evidence':['هر دو مطالعه از داده‌های فرایندی برای پشتیبانی تصمیم‌گیری استفاده می‌کنند.'],
  'conflicting_evidence':[], 'explanations_for_conflicts':[],
  'research_gaps':['اعتبارسنجی برون‌کارخانه‌ای مدل پیش‌بینی دمای انتهایی.'],
  'future_directions':['ارزیابی مدل روی چند کارخانه و چند نوع شارژ.'],
  'conclusion':'نتایج اولیه امیدوارکننده‌اند اما کیفیت داده و تعمیم‌پذیری باید بررسی شود.',
  'limitations_of_review':'فقط دو مقاله نمونه هستند و یکی فقط چکیده دارد.','evidence_strength':'محدود؛ نمونه تستی','claims':[{'claim':'داده کارخانه‌ای بر تعمیم مدل اثر دارد','paper_ids':['https://openalex.org/W1'],'support':'limited','reason':'در محدودیت مطالعه ذکر شده است.'}],
  'analysis_mode':'llm_cross_paper_synthesis'
 },
 'logs':['test log'], 'run_id':'smoke-test', 'llm_enabled':True
}

# Test rule extraction independently
paper={'id':'W3','title':'Random forest for steel process','abstract':'Random forest model was applied to predict steel temperature. Results showed RMSE of 9.4 MPa. Further research is needed.'}
fa={'status':'abstract_only','text':paper['abstract'],'pages':[]}
a=rule_based_analysis(paper,fa)
assert a['evidence_level']=='abstract_only'
assert a['methodology'][0]['category']=='یادگیری ماشین'

out=tempfile.mkdtemp(prefix='rm_smoke_')
files=package_outputs(sample,out)
for key,path in files.items():
    assert os.path.exists(path) and os.path.getsize(path)>0, (key,path)
print(json.dumps({k:{'path':v,'bytes':os.path.getsize(v)} for k,v in files.items()},ensure_ascii=False,indent=2))
