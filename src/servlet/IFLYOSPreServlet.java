package servlet;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

/**
 * Servlet implementation class IFLYOSPreServlet
 */
@WebServlet("/IFLYOSPreServlet")
public class IFLYOSPreServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;

	/**
	 * @see HttpServlet#HttpServlet()
	 */
	public IFLYOSPreServlet() {
		super();
	}

	/**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse
	 *      response)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		response.getWriter().append("Served at: ").append(request.getContextPath());
	}

	/**
	 * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse
	 *      response)
	 */
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

		// aiui.log(Level.INFO, "DO POST " + iflyosPostData.toString());

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

}
