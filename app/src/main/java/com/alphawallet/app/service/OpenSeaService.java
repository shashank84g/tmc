package com.alphawallet.app.service;

import android.net.Uri;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Log;
import android.util.LongSparseArray;

import com.alphawallet.app.C;
import com.alphawallet.app.entity.ContractType;
import com.alphawallet.app.entity.nftassets.NFTAsset;
import com.alphawallet.app.entity.opensea.AssetContract;
import com.alphawallet.app.entity.tokens.Token;
import com.alphawallet.app.entity.tokens.TokenFactory;
import com.alphawallet.app.entity.tokens.TokenInfo;
import com.alphawallet.app.repository.KeyProviderFactory;
import com.alphawallet.app.util.JsonUtils;
import com.alphawallet.ethereum.EthereumNetworkBase;
import com.google.gson.Gson;

import org.json.JSONArray;
import org.json.JSONObject;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.reactivex.Single;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.ResponseBody;
import okhttp3.logging.HttpLoggingInterceptor;
import timber.log.Timber;

/**
 * Created by James on 2/10/2018.
 * Stormbird in Singapore
 */

public class OpenSeaService
{
    private final OkHttpClient httpClient;
    private static final int PAGE_SIZE = 50;
    private final Map<String, String> imageUrls = new HashMap<>();
    private static final TokenFactory tf = new TokenFactory();
    private final LongSparseArray<Long> networkCheckTimes = new LongSparseArray<>();
    private final LongSparseArray<Integer> pageOffsets = new LongSparseArray<>();

    public OpenSeaService()
    {
        pageOffsets.clear();
        httpClient = new OkHttpClient.Builder()
                .connectTimeout(C.CONNECT_TIMEOUT, TimeUnit.SECONDS)
                .connectTimeout(C.READ_TIMEOUT, TimeUnit.SECONDS)
                .writeTimeout(C.WRITE_TIMEOUT, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .addInterceptor(new HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.NONE))
                .build();
    }

    private Request buildRequest(long networkId, String api)
    {
        Request.Builder requestB = new Request.Builder()
                .url(api)
                .method("GET", null);

        String apiKey = KeyProviderFactory.get().getOpenSeaKey();
        if (!TextUtils.isEmpty(apiKey)
                && !apiKey.equals("...")
                && com.alphawallet.app.repository.EthereumNetworkBase.hasRealValue(networkId)
        )
        {
            requestB.addHeader("X-API-KEY", apiKey);
        }

        return requestB.build();
    }

    private String executeRequest(long networkId, String api)
    {
        try (okhttp3.Response response = httpClient.newCall(buildRequest(networkId, api)).execute())
        {
            if (response.isSuccessful())
            {
                ResponseBody responseBody = response.body();
                if (responseBody != null)
                {
                    return responseBody.string();
                }
            }
            else
            {
                return JsonUtils.EMPTY_RESULT;
            }
        }
        catch (Exception e)
        {
            Timber.e(e);
        }

        return JsonUtils.EMPTY_RESULT;
    }

    public Single<Token[]> getTokens(String address,
                                     long networkId,
                                     String networkName,
                                     TokensService tokensService)
    {
        return Single.fromCallable(() ->
        {
            int receivedTokens;
            int currentPage = 0;
            Map<String, Token> foundTokens = new HashMap<>();

            long currentTime = System.currentTimeMillis();
            if (!canCheckChain(networkId)) return new Token[0];
            networkCheckTimes.put(networkId, currentTime);

            int pageOffset = pageOffsets.get(networkId, 0);

            Timber.d("Fetch from opensea : %s", networkName);

            do
            {
                String jsonData = fetchAssets(networkId, address, pageOffset);
                if (!JsonUtils.hasAssets(jsonData))
                {
                    return foundTokens.values().toArray(new Token[0]); //on error return results found so far
                }

                JSONObject result = new JSONObject(jsonData);
                JSONArray assets;
                if (result.has("assets"))
                {
                    assets = result.getJSONArray("assets");
                }
                else
                {
                    assets = result.getJSONArray("results");
                }

                receivedTokens = assets.length();
                pageOffset += assets.length();

                //process this page of results
                processOpenseaTokens(foundTokens, assets, address, networkId, networkName, tokensService);
                currentPage++;
            }
            while (receivedTokens == PAGE_SIZE && currentPage <= 3); //fetch 4 pages for each loop

            if (receivedTokens < PAGE_SIZE)
            {
                Timber.d("Reset OpenSeaAPI reads at: %s", pageOffset);
                pageOffsets.put(networkId, 0);
            }
            else
            {
                pageOffsets.put(networkId, pageOffset);
                networkCheckTimes.put(networkId, currentTime - 55 * DateUtils.SECOND_IN_MILLIS); //do another read within 5 seconds
            }

            //now write the contract images
            for (Map.Entry<String, String> entry : imageUrls.entrySet())
            {
                tokensService.addTokenImageUrl(networkId, entry.getKey(), entry.getValue());
            }
            imageUrls.clear();

            return foundTokens.values().toArray(new Token[0]);
        });
    }

    private void processOpenseaTokens(Map<String, Token> foundTokens,
                                      JSONArray assets,
                                      String address,
                                      long networkId,
                                      String networkName,
                                      TokensService tokensService) throws Exception
    {
        final Map<String, Map<BigInteger, NFTAsset>> assetList = new HashMap<>();

        for (int i = 0; i < assets.length(); i++)
        {
            JSONObject assetJSON = assets.getJSONObject(i);
            AssetContract assetContract =
                    new Gson().fromJson(assetJSON.getString("asset_contract"), AssetContract.class);

            if (assetContract != null && !TextUtils.isEmpty(assetContract.getSchemaName()))
            {
                switch (assetContract.getSchemaName())
                {
                    case "ERC721":
                        handleERC721(assetContract, assetList, assetJSON, networkId, foundTokens, tokensService,
                                networkName, address);
                        break;
                    case "ERC1155":
                        handleERC1155(assetContract, assetList, assetJSON, networkId, foundTokens, tokensService,
                                networkName, address);
                        break;
                }
            }
        }
    }

    private void handleERC721(AssetContract assetContract,
                              Map<String, Map<BigInteger, NFTAsset>> assetList,
                              JSONObject assetJSON,
                              long networkId,
                              Map<String, Token> foundTokens,
                              TokensService svs,
                              String networkName,
                              String address) throws Exception
    {
        NFTAsset asset = new NFTAsset(assetJSON.toString());

        BigInteger tokenId = assetJSON.has("token_id") ?
                new BigInteger(assetJSON.getString("token_id"))
                : null;
        if (tokenId == null) return;

        addAssetImageToHashMap(assetContract.getAddress(), assetContract.getImageUrl());

        Token token = foundTokens.get(assetContract.getAddress());
        if (token == null)
        {
            TokenInfo tInfo;
            ContractType type;
            long lastCheckTime = 0;
            Token checkToken = svs.getToken(networkId, assetContract.getAddress());
            if (checkToken != null && (checkToken.isERC721() || checkToken.isERC721Ticket()))
            {
                assetList.put(assetContract.getAddress(), checkToken.getTokenAssets());
                tInfo = checkToken.tokenInfo;
                type = checkToken.getInterfaceSpec();
                lastCheckTime = checkToken.lastTxTime;

                JSONObject collectionJSON = assetJSON.getJSONObject("collection");
                String collectionName = collectionJSON.getString("name");
                if (!TextUtils.isEmpty(collectionName) && (TextUtils.isEmpty(checkToken.tokenInfo.name) || !collectionName.equals(checkToken.tokenInfo.name)))
                {
                    //Update to collection name if the token name is blank, or if the collection name is not blank and current token name is different
                    tInfo = new TokenInfo(assetContract.getAddress(), collectionName, assetContract.getSymbol(), 0, tInfo.isEnabled, networkId);
                }
            }
            else //if we haven't seen the contract before, or it was previously logged as something other than a ERC721 variant then specify undetermined flag
            {
                tInfo = new TokenInfo(assetContract.getAddress(), assetContract.getName(), assetContract.getSymbol(), 0, true, networkId);
                type = ContractType.ERC721_UNDETERMINED;
            }

            token = tf.createToken(tInfo, type, networkName);
            token.setTokenWallet(address);
            token.lastTxTime = lastCheckTime;
            foundTokens.put(assetContract.getAddress(), token);
        }
        asset.updateAsset(tokenId, assetList.get(token.getAddress()));
        token.addAssetToTokenBalanceAssets(tokenId, asset);
    }

    private void handleERC1155(AssetContract assetContract,
                               Map<String, Map<BigInteger, NFTAsset>> assetList,
                               JSONObject assetJSON,
                               long networkId,
                               Map<String, Token> foundTokens,
                               TokensService svs,
                               String networkName,
                               String address) throws Exception
    {
        NFTAsset asset = new NFTAsset(assetJSON.toString());

        BigInteger tokenId = assetJSON.has("token_id") ?
                new BigInteger(assetJSON.getString("token_id"))
                : null;

        if (tokenId == null) return;

        addAssetImageToHashMap(assetContract.getAddress(), assetContract.getImageUrl());

        Token token = foundTokens.get(assetContract.getAddress());
        if (token == null)
        {
            TokenInfo tInfo;
            ContractType type;
            long lastCheckTime = 0;
            Token checkToken = svs.getToken(networkId, assetContract.getAddress());
            if (checkToken != null && checkToken.getInterfaceSpec() == ContractType.ERC1155)
            {
                assetList.put(assetContract.getAddress(), checkToken.getTokenAssets());
                tInfo = checkToken.tokenInfo;
                type = checkToken.getInterfaceSpec();
                lastCheckTime = checkToken.lastTxTime;
            }
            else
            {
                tInfo = new TokenInfo(assetContract.getAddress(), assetContract.getName(), assetContract.getSymbol(), 0, true, networkId);
                type = ContractType.ERC1155;
            }

            token = tf.createToken(tInfo, type, networkName);
            token.setTokenWallet(address);
            token.lastTxTime = lastCheckTime;
            token.setAssetContract(assetContract);
            foundTokens.put(assetContract.getAddress(), token);
        }
        asset.updateAsset(tokenId, assetList.get(token.getAddress()));
        token.addAssetToTokenBalanceAssets(tokenId, asset);
    }

    private void addAssetImageToHashMap(String address, String imageUrl)
    {
        if (!imageUrls.containsKey(address) && !TextUtils.isEmpty(imageUrl))
        {
            imageUrls.put(address, imageUrl);
        }
    }

    public void resetOffsetRead(List<Long> networkFilter)
    {
        long offsetTime = System.currentTimeMillis() - 57 * DateUtils.SECOND_IN_MILLIS;
        for (long networkId : networkFilter)
        {
            if (com.alphawallet.app.repository.EthereumNetworkBase.hasOpenseaAPI(networkId))
            {
                networkCheckTimes.put(networkId, offsetTime);
                offsetTime += 10 * DateUtils.SECOND_IN_MILLIS;
            }
        }
        pageOffsets.clear();
    }

    public boolean canCheckChain(long networkId)
    {
        long lastCheckTime = networkCheckTimes.get(networkId, 0L);
        return System.currentTimeMillis() > (lastCheckTime + DateUtils.MINUTE_IN_MILLIS);
    }

    public Single<String> getAsset(Token token, BigInteger tokenId)
    {
        if (!com.alphawallet.app.repository.EthereumNetworkBase.hasOpenseaAPI(token.tokenInfo.chainId))
        {
            return Single.fromCallable(() -> "");
        }
        else
        {
            return Single.fromCallable(() ->
                    fetchAsset(token.tokenInfo.chainId, token.tokenInfo.address, tokenId.toString()));
        }
    }

    public Single<String> getCollection(Token token, String slug)
    {
        return Single.fromCallable(() ->
                fetchCollection(token.tokenInfo.chainId, slug));
    }

    public String fetchAssets(long networkId, String address, int offset)
    {
        String api = "";
        String ownerOption = "owner";

        Timber.tag("Shashank_Result").e("Calling Network ID:" +networkId);
        //TODO: Put these into a mapping
        if (networkId == EthereumNetworkBase.MAINNET_ID)
        {
            api = C.OPENSEA_ASSETS_API_MAINNET;
        }
        else if (networkId == EthereumNetworkBase.GOERLI_ID)
        {
            api = C.OPENSEA_ASSETS_API_TESTNET;
        }
        else if (networkId == EthereumNetworkBase.POLYGON_ID)
        {
            api = C.OPENSEA_ASSETS_API_MATIC;
            ownerOption = "owner_address";
        }
        else if (networkId == EthereumNetworkBase.ARBITRUM_MAIN_ID)
        {
            api = C.OPENSEA_ASSETS_API_ARBITRUM;
            ownerOption = "owner_address";
        }
        else if (networkId == EthereumNetworkBase.AVALANCHE_ID)
        {
            api = C.OPENSEA_ASSETS_API_AVALANCHE;
            ownerOption = "owner_address";
        }
        else if (networkId == EthereumNetworkBase.KLAYTN_ID)
        {
            api = C.OPENSEA_ASSETS_API_KLAYTN;
            ownerOption = "owner_address";
        }
        else if (networkId == EthereumNetworkBase.OPTIMISTIC_MAIN_ID)
        {
            api = C.OPENSEA_ASSETS_API_OPTIMISM;
            ownerOption = "owner_address";
        }

        String returnResponse="";
        if (!TextUtils.isEmpty(api))
        {
            Uri.Builder builder = new Uri.Builder();
            builder.encodedPath(api)
                .appendQueryParameter(ownerOption, address)
                .appendQueryParameter("limit", String.valueOf(PAGE_SIZE))
                .appendQueryParameter("offset", String.valueOf(offset));
            returnResponse = executeRequest(networkId, builder.build().toString());
//            Timber.tag("Shashank_fetch_1").e(returnResponse + " ");
            return returnResponse;
        }
        returnResponse = JsonUtils.EMPTY_RESULT;
//        Timber.tag("Shashank_fetch_1").e(returnResponse + " ");

        return returnResponse;
    }

    public String fetchAsset(long networkId, String contractAddress, String tokenId)
    {
        String api = "";
        if (networkId == EthereumNetworkBase.MAINNET_ID)
        {
            api = C.OPENSEA_SINGLE_ASSET_API_MAINNET + contractAddress + "/" + tokenId;
        }
        else if (networkId == EthereumNetworkBase.GOERLI_ID)
        {
            api = C.OPENSEA_SINGLE_ASSET_API_TESTNET + contractAddress + "/" + tokenId;
        }
        else if (networkId == EthereumNetworkBase.POLYGON_ID)
        {
            api = C.OPENSEA_SINGLE_ASSET_API_MATIC + contractAddress + "/" + tokenId;
        }
        else if (networkId == EthereumNetworkBase.ARBITRUM_MAIN_ID)
        {
            api = C.OPENSEA_SINGLE_ASSET_API_ARBITRUM + contractAddress + "/" + tokenId;
        }
        else if (networkId == EthereumNetworkBase.AVALANCHE_ID)
        {
            api = C.OPENSEA_SINGLE_ASSET_API_AVALANCHE + contractAddress + "/" + tokenId;
        }
        else if (networkId == EthereumNetworkBase.KLAYTN_ID)
        {
            api = C.OPENSEA_SINGLE_ASSET_API_KLAYTN + contractAddress + "/" + tokenId;
        }
        else if (networkId == EthereumNetworkBase.OPTIMISTIC_MAIN_ID)
        {
            api = C.OPENSEA_SINGLE_ASSET_API_OPTIMISM + contractAddress + "/" + tokenId;
        }
        String returnResponse = executeRequest(networkId, api);
//        Timber.tag("Shashank_fetch_2").e(returnResponse + " ");

        return returnResponse;
    }

    public String fetchCollection(long networkId, String slug)
    {
        String api = C.OPENSEA_COLLECTION_API_MAINNET + slug;
        String returnResponse = executeRequest(networkId, api);
//        Timber.tag("Shashank_fetch_3").e(returnResponse + " ");
        return returnResponse;
    }
}
