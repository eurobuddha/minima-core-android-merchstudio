package com.eurobuddha.merchstudio;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;
/** Fixtures reused from Pandamonium UtxoTokenNamesTest / NftTokenNamesTest. */
public class TokenMetadataParityTest {
    private JSONObject name(Object value)throws Exception{return new JSONObject().put("name",value);}
    @Test public void serializedMetadataKeepsNameIconAndOriginalPayload()throws Exception{
        String image="data:image/png;base64,"+String.join("",java.util.Collections.nCopies(32768,"AAAA"));
        JSONObject details=name("Колекція 中文").put("url",image).put("ticker","NFT");
        for(Object token:new Object[]{details.toString(),name(details).toString(),name(details.toString()),name(details.toString()).toString()}){
            String before=token.toString();TokenMeta meta=TokenMeta.parse(token,"0x1234");
            assertEquals("Колекція 中文",meta.name);assertEquals(meta.name,Util.tokenName(token,"0x1234"));
            assertEquals(image,meta.iconUrl);assertEquals("NFT",meta.ticker);assertEquals(before,token.toString());
        }
    }
    @Test public void missingSerializedNamesDoNotDisplayJson()throws Exception{
        for(Object token:new Object[]{"{}",name(new JSONObject()).toString(),name("{}"),name(JSONObject.NULL).toString()}){
            assertEquals("Token",TokenMeta.parse(token,"0x1234").name);assertEquals("Token",Util.tokenName(token,"0x1234"));
        }
        assertEquals("{unfinished",TokenMeta.parse("{unfinished","0x1234").name);
    }
    @Test public void authoredNamesRemainLiteral()throws Exception{
        for(String literal:new String[]{"Token","Send","中文 Русский Українська {amount} $1","[1,2]"})
            for(Object token:new Object[]{literal,name(literal),name(name(literal))}){
                assertEquals(literal,TokenMeta.parse(token,"0x1234").name);assertEquals(literal,Util.tokenName(token,"0x1234"));
            }
    }
    @Test public void serializedCollectionsKeepCoverAndImagePath()throws Exception{
        JSONObject details=name("Collection 中文 Українська").put("mode","url").put("size",12)
                .put("base","https://example.invalid/nft/").put("ext",".png").put("url","https://example.invalid/cover.png");
        for(Object metadata:new Object[]{details,details.toString()}){
            JSONObject token=name(metadata).put("totalamount","12");
            for(Object value:new Object[]{token,token.toString()}){
                JSONObject envelope=new JSONObject().put("token",value).put("script","RETURN TRUE");String before=envelope.toString();
                for(Object record:new Object[]{envelope,envelope.toString(),value}){
                    StateNft.Meta meta=StateNft.parseMeta("0x1234",record);
                    assertEquals("Collection 中文 Українська",meta.name);assertEquals(12,meta.size);assertEquals("url",meta.mode);assertTrue(StateNft.isCandidate(meta));
                    assertEquals("https://example.invalid/cover.png",meta.icon);
                    assertEquals("https://example.invalid/nft/3.png",StateNft.imageUrl(meta,3,null));
                    TokenMeta cover=TokenMeta.parse(record,"0x1234");assertEquals(meta.name,cover.name);assertEquals(meta.icon,cover.iconUrl);
                }
                assertEquals(before,envelope.toString());
            }
        }
    }
    @Test public void unnamedCollectionCannotBecomeJsonImagePayload()throws Exception{
        JSONObject details=new JSONObject().put("url","https://example.invalid/cover.png").put("size",12);
        for(Object value:new Object[]{details,details.toString()}){
            assertEquals("Collection",StateNft.parseMeta("0x1234",name(value)).name);
            assertEquals("Token",TokenMeta.parse(name(value),"0x1234").name);assertEquals("Token",Util.tokenName(name(value),"0x1234"));
        }
    }
}
