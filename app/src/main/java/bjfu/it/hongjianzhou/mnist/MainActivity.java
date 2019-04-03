package bjfu.it.hongjianzhou.mnist;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;

import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;


public class MainActivity extends Activity{

    public static final int REQUEST_CODE = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }


    public void onClickButton(View view) {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*");
        startActivityForResult(intent, REQUEST_CODE);
    }


    protected void onActivityResult(int requestCode, int resultCode, Intent data){
        if (requestCode == REQUEST_CODE) {
            // 从相册返回的数据
            if (data != null) {
                // 得到图片的全路径
                Uri uri = data.getData();
                try(FileInputStream in = new FileInputStream(getFileByUri(uri,this))){
                    byte[] photo2Byte = new byte[in.available()];
                    in.read(photo2Byte);
                    //将图片转为BASE64格式
                    String Base64String = Base64.encodeToString(photo2Byte, Base64.DEFAULT);
                    //去除字符串中的不必要的回车空格等字符
                    String encodedStr = Pattern.compile("\\s*|\t|\r|\n").matcher(Base64String).replaceAll("");
                    TextView textView = findViewById(R.id.textView);
                    textView.setText(R.string.request);
                    CallAIStudioTask recognizeTask = new CallAIStudioTask();
                    recognizeTask.execute(encodedStr);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                    Toast.makeText(this,"获取该图片失败(缺少权限)",Toast.LENGTH_LONG).show();
                } catch (IOException e){
                    e.printStackTrace();
                    Toast.makeText(this,"图片读取失败",Toast.LENGTH_LONG).show();
                }

                ImageView photo = findViewById(R.id.photo);
                photo.setImageURI(uri);
            }
        }
    }


    public File getFileByUri(Uri uri, Context context) {
        String path = null;
        String[] projection = { MediaStore.Images.Media.DATA };
        Cursor cursor = context.getContentResolver().query(uri, projection, null, null, null);
        if (cursor!=null && cursor.moveToFirst()) {
            int columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            path = cursor.getString(columnIndex);
            cursor.close();
        }
        if(path!=null)
            return new File(path);
        else
            return null;
    }

    class CallAIStudioTask extends AsyncTask<String,Void,String> {
        @Override
        protected String doInBackground(String... params) {
            String result = "";
            //构造JSON键值对（变量名要和AI Studio部署模型的输入参数相同）
            TextView textView = findViewById(R.id.textView);
            try {
                String json = "{\"img\":\"" + params[0] + "\"}";
                byte[] data = json.getBytes(StandardCharsets.UTF_8);
                //部署AI Studio后根据 [服务地址] [?] [apiKey=xxx]生成URL
                URL url = new URL("https://aistudio.baidu.com/serving/online/395?apiKey=fd395bec-91ff-4be8-ab4a-e22609812c76");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(3000);
                connection.setDoInput(true);//表示从服务器获取数据
                connection.setDoOutput(true);//表示向服务器写数据

                connection.setRequestMethod("POST");
                //是否使用缓存
                connection.setUseCaches(false);
                //表示设置请求体的类型是文本类型
                connection.setRequestProperty("Content-Type", "application/x-javascript; charset=UTF-8");
                connection.setRequestProperty("Content-Length", String.valueOf(data.length));

                try(OutputStream out = connection.getOutputStream()) {
                    out.write(data);
                    out.flush();
                    InputStream in= connection.getInputStream();
                    // 响应代码 200表示成功
                    Log.d("响应代码",String.valueOf(connection.getResponseCode()));
                    if (connection.getResponseCode() == 200) {
                        result = new String(toByteArray(in), StandardCharsets.UTF_8);
                        Log.d("result",result);
                    }
                }catch (IOException e){
                    e.printStackTrace();
                    Log.d("错误信息","图片上传失败");
                    textView.setText(R.string.errorUploadFailed);
                }

            }catch (MalformedURLException e) {
                e.printStackTrace();
                Log.d("错误信息","URL错误");
                textView.setText(R.string.errorUrlWrong);
            }catch (IOException e){
                e.printStackTrace();
                Log.d("错误信息","建立连接失败");
                textView.setText(R.string.errorConnectFailed);
            }
            return result;
        }


        private byte[] toByteArray(InputStream input) throws IOException {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int n = input.read(buffer);
            if (n != -1) {
                output.write(buffer, 0, n);
            }
            return output.toByteArray();
        }

        @Override
        protected void onPostExecute(String response) {
            //参数response是方法“doInBackground()”的返回值
            TextView textView = findViewById(R.id.textView);
            if(response.contains("服务调用失败"))
                textView.setText(R.string.errorRequestFailed);
            else if(response.contains("不是运行中状态"))
                textView.setText(R.string.errorAPINotRunning);
            else {
                StringBuffer result = new StringBuffer();
                String splitedResult[] = response.split("[{}:]");
                result.append("识别结果：").append(splitedResult[splitedResult.length - 1]);
                textView.setText(result);
            }
        }

    }

}
