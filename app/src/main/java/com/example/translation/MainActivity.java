package com.example.translation;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.lang.reflect.Type;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {
    private TextView showText ;
    private Button querybtn ;
    private EditText inputEditext ;

    private String YouDaoBaseUrl = "http://fanyi.youdao.com/openapi.do";
    private String YouDaoKeyFrom = "TranslationBest";
    private String YouDaoKey = "1718711317";
    private String YouDaoType = "data";
    private String YouDaoDoctype = "json";
    private String YouDaoVersion = "1.1";
    private TranslateHandler handler;
    String message = null ;
    private static final int SUCCEE_RESULT = 10;
    private static final int ERROR_TEXT_TOO_LONG = 20;
    private static final int ERROR_CANNOT_TRANSLATE = 30;
    private static final int ERROR_UNSUPPORT_LANGUAGE = 40;
    private static final int ERROR_WRONG_KEY = 50;
    private static final int ERROR_WRONG_RESULT = 60;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        showText = (TextView) findViewById(R.id.tv_show);
        inputEditext = (EditText) findViewById(R.id.et_input);
        querybtn = (Button) findViewById(R.id.btn_query);
        handler = new TranslateHandler(this , showText) ;

        querybtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String content = inputEditext.getText().toString().trim() ;
                if (content == null && "".equals(content)){
                    Toast.makeText(getApplicationContext(), "请输入要翻译的内容", Toast.LENGTH_SHORT).show();
                    return;
                }
                final String YouDaoUrl = YouDaoBaseUrl + "?keyfrom=" + YouDaoKeyFrom + "&key=" + YouDaoKey + "&type="
                        + YouDaoType + "&doctype=" + YouDaoDoctype + "&type=" + YouDaoType + "&version="
                        + YouDaoVersion + "&q=" + content ;
                new Thread(){
                    @Override
                    public void run() {
                        try{
                            AnalyzingOfJson(YouDaoUrl) ;
                        }catch (Exception e){
                            e.printStackTrace();
                        }
                    }
                }.start();
            }
        });


    }

    private void AnalyzingOfJson(String url) throws Exception {
        OkHttpClient client = new OkHttpClient() ;
        Request request = new Request.Builder()
                .url(url)
                .build() ;
        Response response = client.newCall(request).execute() ;
        String responseData = response.body().string() ;
        JSONArray jsonArray = new JSONArray("[" + responseData + "]") ;
        for (int i = 0 ; i < jsonArray.length() ; i++){
            JSONObject jsonObject = jsonArray.getJSONObject(i) ;
            if (jsonObject != null){
                String errorCode = jsonObject.getString("errorCode") ;
                if (errorCode.equals("20")){
                    handler.sendEmptyMessage(ERROR_TEXT_TOO_LONG) ;
                }else if (errorCode.equals("30")){
                    handler.sendEmptyMessage(ERROR_CANNOT_TRANSLATE) ;
                }else if (errorCode.equals("40")){
                    handler.sendEmptyMessage(ERROR_UNSUPPORT_LANGUAGE) ;
                }else if (errorCode.equals("50")){
                    handler.sendEmptyMessage(ERROR_WRONG_KEY) ;
                }else if (errorCode.equals("60")){
                    handler.sendEmptyMessage(ERROR_WRONG_RESULT) ;
                } else {
                    Message msg = new Message() ;
                    msg.what = SUCCEE_RESULT ;
                    String querystr = jsonObject.getString("query") ;
                    message = "翻译结果："  ;
                    //翻译内容
                    Gson gson = new Gson() ;
                    Type type = new TypeToken<String[]>(){}.getType() ;
                    String[] translations = gson.fromJson(jsonObject.getString("translation") , type) ;
                    for (String translation : translations){
                        message += "\t" + translation ;
                    }

                    //有道词典-基本词典
                    if (jsonObject.has("basic")){
                        JSONObject basic = jsonObject.getJSONObject("basic") ;
                        if (basic.has("phonetic")){
                            String phonetic = basic.getString("phonetic") ;
                            message += "\n\t" + phonetic;
                        }
                        if (basic.has("explains")){
                            String explains = basic.getString("explains") ;
                            message += "\n\t" + explains ;
                        }
                    }

                    //有道词典-网络释义
                    if (jsonObject.has("web")){
                        String web = jsonObject.getString("web") ;
                        JSONArray webString = new JSONArray("[" + web + "]") ;
                        message += "\n网络释义" ;
                        JSONArray webArray = webString.getJSONArray(0) ;
                        int count = 0 ;
                        while(!webArray.isNull(count)){
                            if (webArray.getJSONObject(count).has("key")){
                                String key = webArray.getJSONObject(count).getString("key") ;
                                message += "\n（" + (count + 1) + "）" + key + "\n";
                            }
                            if (webArray.getJSONObject(count).has("value")){
                                String[] values = gson.fromJson(webArray.getJSONObject(count).getString("value") ,type) ;
                                for (int j = 0 ; j < values.length ; j++){
                                    String value = values[j] ;
                                    message += value ;
                                    if (j < values.length - 1){
                                        message += "，" ;
                                    }
                                }
                            }
                            count ++ ;
                        }
                    }
                    msg.obj = message ;
                    handler.sendMessage(msg) ;
                }
            }
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                showText.setText(message);
            }
        });

    }

    private class TranslateHandler extends Handler{
        private Context mContext ;
        private TextView mTextView ;

        public TranslateHandler(Context context , TextView textView){
            this.mContext = context ;
            this.mTextView = textView ;
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what){
                case SUCCEE_RESULT :
                    mTextView.setText((String) msg.obj);
                    closeInput() ;
                    break;
                case ERROR_TEXT_TOO_LONG :
                    Toast.makeText(mContext , "要翻译的文本过长" , Toast.LENGTH_SHORT).show();
                    break;
                case ERROR_CANNOT_TRANSLATE :
                    Toast.makeText(mContext, "无法进行有效的翻译", Toast.LENGTH_SHORT).show();
                    break;
                case ERROR_UNSUPPORT_LANGUAGE:
                    Toast.makeText(mContext, "不支持的语言类型", Toast.LENGTH_SHORT).show();
                    break;
                case ERROR_WRONG_KEY:
                    Toast.makeText(mContext, "无效的key", Toast.LENGTH_SHORT).show();
                    break;
                case ERROR_WRONG_RESULT:
                    Toast.makeText(mContext, "提取异常", Toast.LENGTH_SHORT).show();
                    break;
                default:
                    break;
            }
            super.handleMessage(msg);
        }
    }

    private void closeInput() {
        InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if ((inputMethodManager != null) && (this.getCurrentFocus() != null)){
            inputMethodManager.hideSoftInputFromWindow(this.getCurrentFocus().getWindowToken() , InputMethodManager.HIDE_NOT_ALWAYS) ;
        }
    }
}
