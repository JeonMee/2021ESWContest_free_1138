package org.pleaselast.administrator;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import org.json.JSONObject;

import java.util.HashMap;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.Body;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.GET;
import retrofit2.http.Headers;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.PUT;

public class MainActivity extends AppCompatActivity {

    private EditText editText1;
    private EditText editText2;

    Retrofit retrofit = new Retrofit.Builder()
            .baseUrl("http://192.168.50.175:5000")
            .addConverterFactory(GsonConverterFactory.create())
            .build();
    RetrofitAPI retrofitAPI = retrofit.create(RetrofitAPI.class);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        editText1 = findViewById(R.id.EditText1);
        editText2 = findViewById(R.id.EditText2);
    }

    public interface RetrofitAPI{
        @PUT("/Pressure")
        @Headers("Content-Type: application/json")
        Call<Post> putPressure(@Body JSONObject pressure);
    }

    public void onClick_getPressure(View v) {
        double pressure1 = Double.parseDouble(editText1.getText().toString());
        double pressure2 = Double.parseDouble(editText2.getText().toString());

        HashMap<String, Object> input = new HashMap<>();
        input.put("pressure1", pressure1);
        input.put("pressure2", pressure2);
        JSONObject p = new JSONObject(input);
        //HashMap 형태의 데이터를 JSON 형태로 변환

        try {
            retrofitAPI.putPressure(p).enqueue(new Callback<Post>() {
                @Override
                public void onResponse(@NonNull Call<Post> call, @NonNull Response<Post> response) {
                    if (response.isSuccessful()) {
                        Toast.makeText(MainActivity.this, "서버 전송 성공", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(MainActivity.this, "실패(response 실패)", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(@NonNull Call<Post> call, @NonNull Throwable t) {
                    Toast.makeText(MainActivity.this, "서버에 저장 되었습니다", Toast.LENGTH_LONG).show();
                }
            });
        } catch(Exception e) {
            Toast.makeText(MainActivity.this, "예외 : " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }

    }
}













