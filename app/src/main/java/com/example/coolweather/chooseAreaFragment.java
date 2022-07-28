package com.example.coolweather;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;
import androidx.lifecycle.LifecycleOwner;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.coolweather.db.City;
import com.example.coolweather.db.County;
import com.example.coolweather.db.Province;
import com.example.coolweather.util.DataProcess;
import com.example.coolweather.util.HttpUtil;

import org.jetbrains.annotations.NotNull;
import org.litepal.crud.DataSupport;

import java.io.IOException;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;


public class chooseAreaFragment extends Fragment {
    public static final int LEVEL_PROVINCE=0;
    public static final int LEVEL_CITY=1;
    public static final int LEVEL_COUNTY=2;

    private ProgressDialog progressDialog;
    private TextView titleText;
    private Button backButton;

    private ListView listView;
    private ArrayAdapter<String> adapter;
//    这就是放名字的到时候
    private List<String> dataList=new ArrayList<>();

    private List<Province> provinceList;
    private List<City> cityList;
    private List<County> countyList;

    private Province selectedProvince;
    private City selectedCity;
    private int currentLevel;

    @Override
    public void onAttach(@NonNull @NotNull Context context) {
        super.onAttach(context);

        //requireActivity() 返回的是宿主activity
        requireActivity().getLifecycle().addObserver(new LifecycleEventObserver() {
            @Override
            public void onStateChanged(@NonNull @NotNull LifecycleOwner source, @NonNull @NotNull Lifecycle.Event event) {
                if (event.getTargetState() == Lifecycle.State.CREATED){
                    //在这里任你飞翔
                    listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                        @Override
                        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                            if (currentLevel == LEVEL_PROVINCE) {
                                selectedProvince = provinceList.get(position);
                                queryCities();
                            } else if (currentLevel == LEVEL_CITY) {
                                selectedCity = cityList.get(position);
                                queryCounties();
                            }
                            else if (currentLevel == LEVEL_COUNTY) {
                                String weatherId = countyList.get(position).getWeatherId();
                                if (getActivity() instanceof MainActivity) {
                                    Intent intent = new Intent(getActivity(), WeatherActivity.class);
                                    intent.putExtra("weather_id", weatherId);
                                    startActivity(intent);
                                    getActivity().finish();
                                } else if (getActivity() instanceof WeatherActivity) {
                                    WeatherActivity activity = (WeatherActivity) getActivity();
                                    activity.drawerLayout.closeDrawers();
                                    activity.swipeRefresh.setRefreshing(true);
                                    activity.requestWeather(weatherId);
                                }
                            }
                        }
                    });
                    //上面设置点击子项
                    backButton.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            if (currentLevel == LEVEL_COUNTY) {
                                queryCities();
                            } else if (currentLevel == LEVEL_CITY) {
                                queryProvinces();
                            }
                        }
                    });
                    queryProvinces();

                    getLifecycle().removeObserver(this);  //这里是删除观察者
                }
            }
        });
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v=inflater.inflate(R.layout.choose_area, container, false);
        titleText=v.findViewById(R.id.title_text);
        backButton=v.findViewById(R.id.back_button);
        listView=v.findViewById(R.id.list_view);

        adapter=new ArrayAdapter<>(getContext(), android.R.layout.simple_list_item_1,dataList);
        listView.setAdapter(adapter);
        return v;
    }

    private void queryProvinces() {
        titleText.setText("中国");
        backButton.setVisibility(View.GONE);
        provinceList= DataSupport.findAll(Province.class);
        if(provinceList.size()>0){
            dataList.clear();
            for(Province pro:provinceList){
                dataList.add(pro.getProvinceName());
            }
            adapter.notifyDataSetChanged();
            listView.setSelection(0);
            currentLevel=LEVEL_PROVINCE;
        }
        else{
//            如果没数据，则会查询服务器，然后保存到数据库，再调用query方法，这样保证可以设置到currentLevel
            String url="http://guolin.tech/api/china/";
            queryServer(url,"province");
        }
    }



    private void queryCities() {
        titleText.setText(selectedProvince.getProvinceName());
        backButton.setVisibility(View.VISIBLE);
//        这样不对，我们只要某个省的城市，不是要数据库里所有的城市
        cityList=DataSupport.where("provinceId=?",String.valueOf(selectedProvince.getId())).find(City.class);
        if(cityList.size()>0){
            dataList.clear();
            for(City c:cityList){
                dataList.add(c.getCityName());
            }
            currentLevel=LEVEL_CITY;
            adapter.notifyDataSetChanged();
            listView.setSelection(0);
        }
        else{
//            用于要获取的拼凑url
            int code=selectedProvince.getProvinceCode();
            String url="http://guolin.tech/api/china/"+code;
            queryServer(url,"city");
        }
    }

    private void queryCounties() {
        titleText.setText(selectedCity.getCityName());
        backButton.setVisibility(View.VISIBLE);
        countyList=DataSupport.where("cityId=?",String.valueOf(selectedCity.getId())).find(County.class);

        if (countyList.size()>0){
            dataList.clear();
            for (County c:countyList){
                dataList.add(c.getCountyName());
            }
            currentLevel=LEVEL_COUNTY;
            adapter.notifyDataSetChanged();
            listView.setSelection(0);

        }
        else{
            int pCode=selectedProvince.getProvinceCode();
            int cCode=selectedCity.getCityCode();
            String url="http://guolin.tech/api/china/"+pCode+"/"+cCode;
            queryServer(url,"county");
        }
    }
    private void queryServer(String url,String type) {
        showProgressDialog();
        HttpUtil.sendOkHttpRequest(url, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        closeProgressDialog();
                        Toast.makeText(getContext(),"加载失败",Toast.LENGTH_SHORT).show();
                    }
                });

            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                boolean result=false;
                String responseText=response.body().string();
                if ("province".equals(type)){
                    result= DataProcess.handleProvinceResponse(responseText);
                }
                if ("city".equals(type)) {
                    result=DataProcess.handleCityResponse(responseText,selectedProvince.getId());
                }
                if ("county".equals(type)){
                    result=DataProcess.handleCountyResponse(responseText,selectedCity.getId());
                }
                if(result){
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            closeProgressDialog();
                            if(type.equals("province")){
                                queryProvinces();
                            }
                            if (type.equals("city")){
                                queryCities();
                            }
                            if (type.equals("county")){
                                queryCounties();
                            }
                        }

                    });
                }
            }

        });
    }


    private void closeProgressDialog() {
        if(progressDialog==null){
            progressDialog=new ProgressDialog(getActivity());
            progressDialog.setMessage("正在加载");
            progressDialog.setCanceledOnTouchOutside(false);
        }
        progressDialog.show();
    }

    private void showProgressDialog() {
        if (progressDialog!=null){
            progressDialog.dismiss();
        }
    }

}

