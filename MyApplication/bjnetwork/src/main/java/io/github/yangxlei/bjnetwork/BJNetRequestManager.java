package io.github.yangxlei.bjnetwork;

import android.text.TextUtils;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.CacheControl;
import okhttp3.Call;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Created by yanglei on 16/6/23.
 */
public class BJNetRequestManager {

    public enum HttpMethod {
        GET("GET"), POST("POST"), DELETE("DELETE"), PUT("PUT");

        String method;
        HttpMethod(String method) {
            this.method = method;
        }

        public String getMethod() {
            return method;
        }
    }

    private BJNetworkClient mNetworkClient;
    private BJNetResourceManager mResourceManager;
    public BJNetRequestManager(BJNetworkClient client) {
        assert (client != null);

        this.mNetworkClient = client;

        mResourceManager = new BJNetResourceManager();
    }

    public void cancelCalls(Object tag) {
        mResourceManager.removeAll(tag);
    }

    /**
     * GET 请求接口
     * @param url 请求地址
     * @return {@link BJNetCall}
     */
    public BJNetCall newGetCall(String url) {
        return newGetCall(url, null, 0);
    }

    /**
     * GET 请求接口
     * @param url 请求地址
     * @param headers 自定义请求头
     * @return {@link BJNetCall}
     */
    public BJNetCall newGetCall(String url, Map<String, String> headers) {
        return newGetCall(url, headers, 0);
    }

    /**
     * GET 请求接口
     * @param url 请求地址
     * @param cacheTime 请求缓存时间, 单位秒. 如果缓存有效, 不会再发起同样的请求,并且在无网情况下返回缓存的响应
     * @return {@link BJNetCall}
     */
    public BJNetCall newGetCall(String url, int cacheTime) {
        return newGetCall(url, null, cacheTime);
    }

    /**
     * GET 请求接口
     * @param url 请求地址
     * @param headers 自定义请求头
     * @param cacheTime 请求缓存时间, 单位秒. 如果缓存有效, 不会再发起同样的请求,并且在无网情况下返回缓存的响应
     * @return {@link BJNetCall}
     */
    public BJNetCall newGetCall(String url, Map<String, String> headers, int cacheTime) {
        Request request = buildRequest(HttpMethod.GET, null, url, cacheTime, headers);
        Call call = mNetworkClient.newCall(request);

        return new RealNetCall(mResourceManager, call);
    }

    /**
     * Post 请求接口
     * @param url 请求地址
     * @return {@link BJNetCall}
     */
    public BJNetCall newPostCall(String url, BJRequestBody requestBody) {
        return newPostCall(url, requestBody, null);
    }

    /**
     * Post 请求接口
     * @param url 请求地址
     * @param headers 自定义请求头
     * @return {@link BJNetCall}
     */
    public BJNetCall newPostCall(String url, BJRequestBody requestBody, Map<String, String> headers) {
        Request request = buildRequest(HttpMethod.POST, requestBody == null ? null : requestBody.getRequestBody(), url, 0, headers);
        Call call = mNetworkClient.newCall(request);
        return new RealNetCall(mResourceManager, call);
    }

    /**
     * 构建网络请求
     * @param method
     * @param requestBody
     * @param url
     * @param cacheTimeSeconds
     * @param headers
     * @return
     */
    protected Request buildRequest(HttpMethod method, RequestBody requestBody, String url, int cacheTimeSeconds, Map<String, String> headers) {

        if (TextUtils.isEmpty(url)) throw new IllegalArgumentException("url is empty!!");

        Request.Builder builder = new Request.Builder();
        builder.method(method.getMethod(), requestBody);
        builder.url(url);
        if (cacheTimeSeconds > 0) {
            CacheControl cacheControl = new CacheControl.Builder()
                    .maxAge(cacheTimeSeconds, TimeUnit.SECONDS)
                    .build();
            builder.cacheControl(cacheControl);
        }

        if(headers != null && !headers.isEmpty()) {
            Iterator<String> iterator = headers.keySet().iterator();
            while (iterator.hasNext()) {
                String key = iterator.next();
                String value = headers.get(key);
                builder.addHeader(key, value);
            }
        }

        return builder.build();
    }

    public BJNetworkClient getNetworkClient() {
        return mNetworkClient;
    }

    public BJNetResourceManager getResourceManager() {
        return mResourceManager;
    }

    private static class RealNetCall implements BJNetCall {

        private WeakReference<Call> mWeakCall;
        private Call mCall;
        private BJNetResourceManager mResourceManager;
        private RealNetCall(BJNetResourceManager resourceManager, Call call) {
            // call 本身会被 OkHttpClient 中的队列缓存. 请求完成之后会被清除.
            // 在交付 OKHttpClient 执行之前, 对 call 强引用. 执行之后, 对 Call 弱引用
            mCall = call;
            mWeakCall = new WeakReference<>(call);
            this.mResourceManager = resourceManager;
        }

        @Override
        public void cancel() {
            if (getCall() != null) {
                getCall().cancel();
            }
        }

        @Override
        public BJResponse executeSync(Object tag) throws IOException {
            if (mCall == null) {
                throw new IllegalStateException("Already executed.");
            }
            mResourceManager.addNetCall(tag, this);
            try {
                Response response = mCall.execute();
                return new BJResponse(response);
            } finally {
                // 取消对 Call 的强引用
                mCall = null;
            }
        }

        @Override
        public void executeAsync(Object tag, BJNetCallback callback) {
            if (mCall == null) {
                throw new IllegalStateException("Already executed.");
            }
            mResourceManager.addNetCall(tag, this);
            try {
                mCall.enqueue(callback);
            } finally {
                mCall = null;
            }
        }

        @Override
        public boolean isCanceled() {
            if (getCall() == null) {
                return false;
            }
            return getCall().isCanceled();
        }

        @Override
        public boolean isExecuted() {
            if (getCall() == null) {
                // call == null, 说明请求已经完成,被回收了.
                return false;
            }
            return getCall().isExecuted();
        }

        private Call getCall() {
            if (mCall != null) {
                return mCall;
            } else {
                return mWeakCall.get();
            }
        }
    }
}
