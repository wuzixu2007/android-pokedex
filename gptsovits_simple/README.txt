宝可梦播报音频精简生成工具

文件说明：

1. pokemon_texts.jsonl
   这就是需要送给 GPT-SoVITS 的全部文字，共 1318 行。
   每行只有 key、name、text 三项，text 就是实际上传的播报文字。

2. generate_audio.py
   逐行读取文字，POST 到 http://127.0.0.1:9880/tts，保存为 wav/key.wav。
   WAV 已存在时自动跳过，运行中断后重新运行即可继续。

3. config.example.json
   参考配置。

使用方法：

第一步：启动 GPT-SoVITS 新版 API。

runtime\python.exe api_v2.py -a 127.0.0.1 -p 9880

第二步：复制并修改配置。

Copy-Item config.example.json config.json
notepad config.json

必须正确填写：
- reference_audio：参考音频在 GPT-SoVITS 电脑上的绝对路径
- reference_text：参考音频中实际说出的文字

第三步：生成全部音频。

runtime\python.exe generate_audio.py

生成结果位于 wav 文件夹，文件名使用宝可梦记录 key，例如：

p0001_2662b6cbaa.wav

如需查看或修改播报内容，直接编辑 pokemon_texts.jsonl 中对应行的 text。
