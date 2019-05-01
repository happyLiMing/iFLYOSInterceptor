# iFLYOS Project

# 教你玩转iFLYOS前/后拦截器
- 刘立明 2019.5.1
- 该工程是在小飞极客版音箱上使用iFLYOS前拦截器实现自定义技能开发的示例

## 背景
iFLYOS平台推出以来，有越来越多的开发者加入了体验的行列，并且有越来越多体验非常棒的音箱涌入市场。也有不少开发者在开发过程中遇到不少问题，为了方便大家能够快速入门体验开发的过程，我还是决定将开发流程依次跟大家讲解。

### 准备
- 1 登录 [iFLYOS开放平台](https://www.iflyos.cn/)，添加设备。设备接入-开始接入-接入设备。设备创建完成后会生成一个client_id，牢记该id。

- 2 替换设备端client_id。我们以小飞极客版音箱为例，进入[开发者模式](https://doc.iflyos.cn/ling_dev_mode.html)修改client_id后需重新配置网络直至可正常唤醒。

- 3 你自己的公网服务器

- 4 具备一点简单服务端开发的技能

### 需求说明
#### 简单版对话场景
	你：蓝小飞

	音箱：提示语或指示灯

	你：打开i讯飞

	音箱：好的，已为您打开i讯飞

#### 需求讨论
该功能简单，可以直接复用之前在AIUI开放平台上创建的自定义技能。即时没有也可以用很快的时间来实现，比较简单。

#### 方案设计
如果没有可以直接复用的技能，首先我们[创建自定义技能](https://studio.iflyos.cn/skills),技能类型选择私有技能，服务平台选择AIUI2.0协议。如果早已开发并发布该技能，此步骤则可以忽略。

 - 创建自定义技能
 - 编写语料	 打开{app} app绑定开放实体 IFLYTEK.APP,构建测试没有问题后进行发布。如果对自定义技能开发还不熟悉，可以先参考[自定义技能开发教程](https://doc.iflyos.cn/studio/#%E6%8A%80%E8%83%BD)
 - 创建webapi应用并配置该技能
 - 在 准备阶段 创建的设备中配置前置拦截器。操作步骤如图

 ![](https://github.com/happyLiMing/iFLYOSInterceptor/blob/master/lan_1.png)

 ![](https://github.com/happyLiMing/iFLYOSInterceptor/blob/master/lan_2.png)

#### 服务开发

写代码之前，我们要先搞清楚数据是如何传递的。其实我们可以这么理解：小飞音箱被唤醒后交互的音频被送入云端识别成文本，云端服务使用aiui 的webapi接口发送文本（打开i讯飞）请求，并将aiui 服务返回的语义结果返回拦截器回调地址。前置拦截器接到语义结果后有两种处理方式：

-  想要自己处理的，body返回OS标准的指令响应给到音箱，使用指令控制音箱合成、音频播放、闹钟等。
-  自己不想处理的，返回204状态码，body内容为空即可，识别文本变交由开放技能处理，开放技能处理不了再交给后拦截器。

#### 代码实现

##### step 1 新建Servlet,接收post响应
 
```
	
protected void doPost(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {

		Logger iflyos = Logger.getLogger("iflyos_log");

		// 获取拦截器收到的响应内容
		ServletInputStream inputStream = request.getInputStream();
		InputStreamReader inputStreamReader = new InputStreamReader(inputStream, "UTF-8");
		BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
		String line = "";
		StringBuilder iflyosPostData = new StringBuilder();
		while ((line = bufferedReader.readLine()) != null) {
			iflyosPostData.append(line);
		}

		JSONObject iflyosData = (JSONObject) JSONObject.parse(iflyosPostData.toString());

		iflyos.log(Level.INFO, "iflyosdata " + iflyosData.toJSONString());
		
		JSONObject aiui = iflyosData.getJSONObject("request").getJSONObject("aiui");
		
		JSONObject body = new JSONObject();
		JSONObject responsedata = new JSONObject();
		JSONObject outputSpeech = new JSONObject();
		
		
		JSONArray directives = new JSONArray();
		JSONObject directive = new JSONObject();
		directive.put("type", "AudioPlayer.Play");
		directives.add(directive);
		responsedata.put("directives", directives);
		
		
		JSONObject intent = aiui.getJSONArray("data").getJSONObject(0).getJSONObject("intent");
		
		//通过rc字段判断是否命中自定义技能
		int rc = intent.getIntValue("rc");
		
		iflyos.log(Level.INFO, "get response form aiui server, rc = " + rc);
	
		if (rc != 4) {
			outputSpeech.put("text", "好的，小飞收到您的请求");
		}

		responsedata.put("outputSpeech", outputSpeech);
		body.put("response", responsedata);
		body.put("version", "1.1");
		
		iflyos.log(Level.INFO, " response body  = " + body.toJSONString());

		// 返回json指令给到音箱
		response.setContentType("application/json;charset=utf-8");
		response.getWriter().append(body.toString());
	} else {
		//如果不能命中自定义技能，返回204交由开放技能处理
		response.setStatus(204);
	}
 }
```

#### step 2 技能指令响应说明

上面的代码其实不难理解，但是可能会让人困惑的是为什么返回的响应是这种格式的，其目的是为了什么呢？我们返回的这部分内容便是os指令的标准格式。技能[响应协议](https://doc.iflyos.cn/service/skill_3rd/Skill_Response.html#body%E7%A4%BA%E4%BE%8B)如下：

```
{
  "version": "1.1",
  "sessionAttributes": {
    "key": "value"
  },
  "response": {
    "outputSpeech": {
      "type": "PlainText",
      "text": "Plain text String to speak"
    },
    "reprompt": {
      "outputSpeech": {
        "type": "PlainText",
        "text": "Plain text String to speak"
      }
    },
     "directives": [
    {
      "type": "AudioPlayer.Play",
      "playBehavior": "ENQUEUE",
      "audioItem": {
        "stream": {
          "type": "AUDIO",
          "url": "https://example.com/audiofile.mp3",
          "token": "S0wiXQZ1rVBkov...",
          "expectedPreviousToken": "f78b7d68...",
          "offsetInMilliseconds": 0
        },
        "metadata": {
          "title": "《十年》",
          "subtitle": "陈奕迅",
          "art": {
            "sources": [
              {
                "url": "https://example.com/brie-album-art.png"
              }
            ]
          }
        }   
      }
    }
  ],
    "expectSpeech": true,
    "shouldEndSession": true
  }
}
```

我们不再对协议内容的字段做详细的解读，大家参考上面的链接即可。因为我们在这里希望音箱使用语音合成的方式回应我们，因此我们的示例中返回的响应格式为：

```
{
    "response": {
        "directives": [
            {
                "type": "AudioPlayer.Play"
            }
        ],
        "outputSpeech": {
            "text": "好的，收到您的请求",
            "type": "PlainText"
        }
    },
    "version": "1.1"
}
```
如果希望设备做出不同的反馈或者实现不同的功能控制，只需要修改directives里面的指令和outputSpeech等内容即可。支持的指令请参考：[设备能力介绍API](https://doc.iflyos.cn/device/interface/)

### 多轮对话场景
上面的示例我们展现了是单次交互，但是实际使用场景中肯定会遇到多轮对话的需求。比如如下场景：
	
	你：蓝小飞

	音箱：提示语或指示灯

	你：打开i讯飞

	音箱：好的，确认要打开i讯飞吗？

	你：好的/取消

### 设计实现
对于多轮对话，我们可以通过技能云函数来实现，但是为了方便，我们直接使用平台上界面的意图确认实现上面对话场景中的需求。技能构建发布后我们发现，拦截器收到的响应变成了如下内容：

```
{
    "request": {
        "aiui": {
            "shouldEndSession": false,
            "semantic": [
                {
                    "template": "打开{apps}",
                    "score": 1,
                    "slots": [
                        {
                            "name": "apps",
                            "end": 4,
                            "value": "美团",
                            "begin": 2,
                            "normValue": "美团"
                        }
                    ],
                    "hazard": false,
                    "entrypoint": "ent",
                    "intent": "default_intent"
                }
            ],
            "data": {},
            "version": "9.0",
            "uuid": "atn219dba0c@dx00071024afc4a10e01",
            "sid": "atn219dba0c@dx00071024afc4a10e01",
            "rc": 0,
            "intentType": "custom",
            "answer": {
                "text": "您确认打开i讯飞吗？",
                "type": "T"
            },
            "vendor": "LMLIU",
            "service": "LMLIU.CALL_APP",
            "semanticType": 0,
            "text": "打开i讯飞",
            "category": "LMLIU.CALL_APP"
        },
        "requestId": "752a26cc-1343-426d-baca-89d292014995",
        "type": "IntentRequest",
        "intent": {
            "slots": {
                "apps": {
                    "name": "apps",
                    "resolutions": {
                        "resolutionsPerAuthority": [
                            {
                                "values": [
                                    {
                                        "value": {
                                            "name": "i讯飞",
                                            "id": ""
                                        }
                                    }
                                ],
                                "authority": "ivs.pre.3596f373-d1af-4839-99e2-0012b9b2b7f7.i讯飞",
                                "status": {
                                    "code": "ER_SUCCESS_MATCH"
                                }
                            }
                        ]
                    },
                    "value": "i讯飞"
                }
            },
            "name": "default_intent"
        },
        "timestamp": "2019-05-01T09:15:48.136922Z"
    },
    "session": {
        "new": false,
        "attributes": {},
        "sessionId": "3R4eOZeEy2RbazV50L49d8t94p5Vpqk8sLEQKdfrRTE7HwczZ4MG7yC40kgXboWp"
    },
    "context": {
        "AudioPlayer": {
            "playerActivity": "IDLE",
            "offsetInMilliseconds": 0,
            "token": ""
        },
        "Custom": {},
        "System": {
            "application": {
                "applicationId": "ivs.skill.pre.3596f373-d1af-4839-99e2-0012b9b2b7f7"
            },
            "location": {
                "lng": "117.27616956075",
                "lat": "31.861054722345"
            },
            "user": {
                "userId": "26303332ac5003e9cf9fdd7e1f543cf6"
            },
            "device": {
                "supportedInterfaces": {
                    "AudioPlayer": {}
                },
                "deviceId": "3596f373-d1af-4839-99e2-0012b9b2b7f7.9c417cb57318"
            }
        }
    },
    "version": "1.0"
}
```
仔细观察，上面的格式里的aiui字段包含的是标准的[aiui语义协议](https://doc.iflyos.cn/aiui/sdk/more_doc/semantic_agreement/summary.html)。此时，我们有两个事情要做，一是我们需要让音箱合成answer里的text内容，二是我们要通知音箱保持录音机开启并继续上轮未结束的会话。
于是，我们对代码做了如下修改：

```
protected void doPost(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {

		Logger iflyos = Logger.getLogger("iflyos_log");

		// get request body.
		ServletInputStream inputStream = request.getInputStream();
		InputStreamReader inputStreamReader = new InputStreamReader(inputStream, "UTF-8");
		BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
		String line = "";
		StringBuilder iflyosPostData = new StringBuilder();
		while ((line = bufferedReader.readLine()) != null) {
			iflyosPostData.append(line);
		}

		JSONObject iflyosData = (JSONObject) JSONObject.parse(iflyosPostData.toString());

		iflyos.log(Level.INFO, "iflyosdata " + iflyosData.toJSONString());
		JSONObject aiui = iflyosData.getJSONObject("request").getJSONObject("aiui");
		
		JSONObject body = new JSONObject();
		JSONObject responsedata = new JSONObject();
		JSONObject outputSpeech = new JSONObject();
		body.put("version", "1.1");
		
		JSONArray directives = new JSONArray();
		JSONObject directive = new JSONObject();
		directive.put("type", "AudioPlayer.Play");
		directives.add(directive);
		responsedata.put("directives", directives);
		
		if (aiui.containsKey("shouldEndSession")) {
			if (aiui.getBoolean("shouldEndSession")) {
				responsedata.put("shouldEndSession", true);
				responsedata.put("expectSpeech", false);
			} else {
				responsedata.put("shouldEndSession", false);
				responsedata.put("expectSpeech", true);
			}
			String text = aiui.getJSONObject("answer").getString("text");
			outputSpeech.put("type", "PlainText");
			outputSpeech.put("text", text);
			
			responsedata.put("outputSpeech", outputSpeech);
			body.put("response", responsedata);
			
			

			iflyos.log(Level.INFO, " response body  = " + body.toJSONString());
			
			// repsonse to AIUI server
			response.setContentType("application/json;charset=utf-8");
			response.getWriter().append(body.toString());
			
		} else {
			JSONObject intent = aiui.getJSONArray("data").getJSONObject(0).getJSONObject("intent");
			int rc = intent.getIntValue("rc");

			iflyos.log(Level.INFO, "get response form aiui server, rc = " + rc);

			if (rc != 4) {
				// 判断会话是否结束
				if (intent.containsKey("shouldEndSession") && !intent.getBoolean("shouldEndSession")) {
					responsedata.put("shouldEndSession", false);
					responsedata.put("expectSpeech", true);
				} else {
					responsedata.put("shouldEndSession", true);
					responsedata.put("expectSpeech", false);
				}
				
				// 判断是否有answer
				if (intent.containsKey("answer")) {
					String text = intent.getJSONObject("answer").getString("text");
					outputSpeech.put("text", text);
				} else {
					outputSpeech.put("text", "好的，小飞收到您的请求");
				}

				responsedata.put("outputSpeech", outputSpeech);
				body.put("response", responsedata);
				iflyos.log(Level.INFO, " response body  = " + body.toJSONString());

				// repsonse to AIUI server
				response.setContentType("application/json;charset=utf-8");
				response.getWriter().append(body.toString());
			} else {
				response.setStatus(204);
			}
		}
	}
```

上面我们下发的text内容不再是“好的，收到您的请求”而是意图确认的内容，也就是answer中的text内容“您确认要打开i讯飞吗？”。同时您可能也注意到，为了让音箱继续新的会话，我们在response中添加了另外两个参数，shouldEndSession 和 expectSpeech.字如其义，这两个参数的含义如下：
![](https://github.com/happyLiMing/iFLYOSInterceptor/blob/master/lan_3.png)


### 总结

到目前为止，您可能已经明白iFLYOS开发的基本流程，并且意识到与AIUI不同的是原来将大量的时间放在客户端的工作现在已经转移到后端服务上来。这样开始会让一部分开发者不适应，但是我们也会发现这样带来了极大的好处，以前需要客户端来发版本升级的过程完全没有了，完全由服务端升级，节省了非常多的成本。最后，祝大家开发愉快！

