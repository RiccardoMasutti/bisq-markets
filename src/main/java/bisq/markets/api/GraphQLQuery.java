package bisq.markets.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.util.*;
import java.util.List;
import java.util.logging.Logger;

public abstract class GraphQLQuery {
    private static final Logger LOG = Logger.getLogger(GraphQLQuery.class.getName());
    private static final Gson gson = new GsonBuilder().serializeNulls().setPrettyPrinting().create();

    static bisq.markets.api.GraphQLQuery forRequest(String path, Map<String, String> params) {
        String sort = params.get("sort");
        if (sort != null) {
            params.put("sort", sort.toUpperCase());
        }

        if (path.startsWith("/api/currencies")) {
            return new CurrenciesQuery();
        } else if (path.startsWith("/api/markets")) {
            return new MarketsQuery();
        } else if (path.startsWith("/api/offers")) {
            return new OffersQuery(params);
        } else if (path.startsWith("/api/depth")) {
            return new DepthQuery(params);
        } else if (path.startsWith("/api/ticker")) {
            return new TickerQuery(params);
        } else if (path.startsWith("/api/trades")) {
            return new TradesQuery(params);
        } else if (path.startsWith("/api/hloc")) {
            return new HlocQuery(params);
        } else if (path.startsWith("/api/volumes")) {
            return new VolumesQuery(params);
        } else {
            return null;
        }
    }

    public Object translateResponse(String response) {
        try {
            return translateSuccessfulResponse(response);
        } catch (Exception e) {
            return translateErrorResponse(response);
        }
    }

    private static class Error {
        String message;
    }
    public static class ErrorResponse  {
        List<Error> errors;
    }
    private Object translateErrorResponse(String response) {
        return gson.fromJson(response,new TypeToken<ErrorResponse>(){}.getType());
    }

    protected abstract Object translateSuccessfulResponse(String response);


    private static class CurrenciesQuery extends GraphQLQuery {
        private static class Currency {
            String code;
            String name;
            String type;
            int precision;
        }
        private static final String currenciesQuery = "{ currencies { code name precision type: currencyTypeLowerCase } }";
        private final String query = currenciesQuery;

        @Override
        protected Object translateSuccessfulResponse(String response) {
            GraphQLResponse<List<Currency>> currencies = gson.fromJson(response,new TypeToken<GraphQLResponse<List<Currency>>>(){}.getType());
            Iterator<Currency> iter = currencies.getData().iterator();
            Map<String,Currency> ret = new HashMap();
            while(iter.hasNext()) {
                Currency currency = iter.next();
                ret.put(currency.code,currency);
            }
            return ret;
        }
    }

    private static class MarketsQuery extends GraphQLQuery {
        private static class Market {
            String pair;
            String lname;
            String rname;
            String lsymbol;
            String rsymbol;
            int lprecision;
            int rprecision;
            String ltype;
            String rtype;
        }
        private static final String marketsQuery = "{ markets { pair lname: lName rname: rName lsymbol: lSymbol lprecision: lPrecision rsymbol: rSymbol rprecision: rPrecision ltype: lTypeLowerCase rtype: rTypeLowerCase } }";
        private final String query = marketsQuery;

        @Override
        protected Object translateSuccessfulResponse(String response) {
            GraphQLResponse<List<Market>> markets = gson.fromJson(response,new TypeToken<GraphQLResponse<List<Market>>>(){}.getType());
            Iterator<Market> iter = markets.getData().iterator();
            Map<String,Market> ret = new HashMap();
            while(iter.hasNext()) {
                Market market = iter.next();
                ret.put(market.pair,market);
            }
            return ret;
        }
    }

    private static class OffersQuery extends GraphQLQuery {
        private static class OpenOffer implements Comparable<OpenOffer> {
            String offer_id;
            String market;
            long offer_date;
            String direction;
            String min_amount;
            String amount;
            String price;
            String volume;
            String payment_method;
            String offer_fee_txid;

            @Override
            public int compareTo(OpenOffer o) {
                if (price.length() - o.price.length() == 0 ){
                    return price.compareTo(o.price);
                } else {
                    return price.length() - o.price.length();
                }
            }
        }
        private static final String offerFields = "{ " +
                "market: marketPair offer_id: id offer_date: offerDate " +
                        "direction: btcDirection min_amount: formattedMinAmount " +
                        "amount: formattedBtcAmount price: formattedPrice " +
                        "volume: formattedBtcVolume payment_method: paymentMethodId " +
                        "offer_fee_txid: offerFeeTxId } ";
        private static final String offersQuery = "query Offers($market: MarketPair!, $direction: Direction)" +
                "{ offers(market: $market, direction: $direction) { " +
                "buys: btcBuys " + offerFields +
                "sells: btcSells " + offerFields + " } }";
        private final String query = offersQuery;
        private final Map<String,String> variables;

        OffersQuery(Map<String,String> params){
            variables = params;
        }

        @Override
        protected Object translateSuccessfulResponse(String response) {
            GraphQLResponse<Map<String,List<OpenOffer>>> offers = gson.fromJson(response,new TypeToken<GraphQLResponse<Map<String,List<OpenOffer>>>>(){}.getType());
            Map<String,List<OpenOffer>> buysAndSells = offers.getData();
            Map<String, Map<String,List<OpenOffer>>> ret = new HashMap<>();
            ret.put(variables.get("market"), buysAndSells);
            return ret;
        }
    }

    private static class DepthQuery extends GraphQLQuery {
        private static class Depth {
            List<String> buys;
            List<String> sells;
        }
        private static final String depthQuery = "query Depth($market: MarketPair!)" +
                "{ offers(market: $market) { " +
                "buys: formattedBuyPrices sells: formattedSellPrices } }";
        private final String query = depthQuery;
        private final Map<String,String> variables;

        DepthQuery(Map<String, String> params){
            variables = params;
        }

        @Override
        protected Object translateSuccessfulResponse(String response) {
            GraphQLResponse<Depth> markets = gson.fromJson(response,new TypeToken<GraphQLResponse<Depth>>(){}.getType());
            Depth depth = markets.getData();
            Map<String,Depth> ret = new HashMap<>();
            ret.put(variables.get("market"), depth);
            return ret;
        }
    }

    private static class TickerQuery extends GraphQLQuery {
        private static class Ticker {
            String market;
            String last;
            String high;
            String low;
            String volume_left;
            String volume_right;
            String buy;
            String sell;
        }
        private static final String tickerQuery = "query Ticker($market: MarketPair)" +
                "{ ticker(market: $market) { market: marketPair " +
                "last: formattedLast high: formattedHigh "+
                "low: formattedLow volume_left: formattedVolumeLeft " +
                "volume_right: formattedVolumeRight buy: formattedBuy sell: formattedSell } }";
        private final String query = tickerQuery;
        private final Map<String,String> variables;

        TickerQuery(Map<String, String> params){
            variables = params;
        }

        @Override
        protected Object translateSuccessfulResponse(String response) {
            GraphQLResponse<List<Ticker>> markets = gson.fromJson(response,new TypeToken<GraphQLResponse<List<Ticker>>>(){}.getType());
            List<Ticker> tickers = markets.getData();
            if (variables.get("market") != null){
                return tickers;
            }
            Map<String,Ticker> ret = new HashMap<>();
            for (Ticker ticker : tickers) {
                ret.put(ticker.market, ticker);
            }
            return ret;
        }
    }

    private static class TradesQuery extends GraphQLQuery {
        private static class Trade {
            String market;
            String direction;
            String price;
            String amount;
            String volume;
            String payment_method;
            String trade_id;
            long trade_date;
        }
        private static final String tradesQuery = "query Trades($market: MarketPair! $direction: Direction "+
                "$timestamp_from: UnixSecs $timestamp_to: UnixSecs " +
                "$limit: Int $sort: Sort ) { " +
                "trades(market: $market direction: $direction timestampFrom: $timestamp_from " +
                "timestampTo: $timestamp_to limit: $limit sort: $sort) " +
                "{ market: marketPair direction price: formattedPrice amount: formattedAmount volume: formattedVolume " +
                "payment_method: paymentMethodId trade_id: offerId trade_date: tradeDate } }";
        private final String query = tradesQuery;

        private final Map<String,Object> variables;

       TradesQuery(Map<String,String> params){
           variables = new HashMap<>();
           Iterator<Map.Entry<String,String>> entries = params.entrySet().iterator();
           while (entries.hasNext()) {
               Map.Entry<String,String> next = entries.next();
               variables.put(next.getKey(), next.getValue());
           }
           if (params.containsKey("limit")) {
                   variables.put("limit", Integer.parseInt(params.get("limit")));
           }
       }

        @Override
        protected Object translateSuccessfulResponse(String response) {
            GraphQLResponse<List<Trade>> ret = gson.fromJson(response,new TypeToken<GraphQLResponse<List<Trade>>>(){}.getType());
            return ret.getData();
        }
    }

    private static class HlocQuery extends GraphQLQuery {
        private static class Hloc {
            long period_start;
            String periodStartDateTime;
            String open;
            String high;
            String low;
            String close;
            String volume_left;
            String volume_right;
            String avg;
        }
        private static final String hlocQuery = "query Hloc($market: MarketPair! "+
                "$timestamp_from: UnixSecs $timestamp_to: UnixSecs " +
                "$interval: Interval) { " +
                "hloc(market: $market timestampFrom: $timestamp_from " +
                "timestampTo: $timestamp_to interval: $interval) " +
                "{ period_start: periodStart periodStartDateTime open: formattedOpen high: formattedHigh low: formattedLow " +
                "close: formattedClose volume_left: formattedVolumeLeft volume_right: formattedVolumeRight "+
                "avg: formattedAvg } }";
        private final String query = hlocQuery;

        private final Map<String,String> variables;

        HlocQuery(Map<String,String> params){
            String interval = params.get("interval");
            if (interval != null) {
                params.put("interval", interval.toUpperCase());
            }
            variables = params;
        }

        @Override
        protected Object translateSuccessfulResponse(String response) {
            GraphQLResponse<List<Hloc>> res = gson.fromJson(response,new TypeToken<GraphQLResponse<List<Hloc>>>(){}.getType());
            List<Map<String,Object>> ret = new ArrayList<>();
            for (Hloc hloc : res.getData()) {
                Map<String,Object> v = new HashMap<>();
                String timestamp = variables.get("timestamp");
                if (timestamp != null && timestamp.equals("no")) {
                    v.put("period_start", hloc.periodStartDateTime);
                } else {
                    v.put("period_start", hloc.period_start);
                }
                v.put("open", hloc.open);
                v.put("high", hloc.high);
                v.put("low", hloc.low);
                v.put("close", hloc.close);
                v.put("volume_left", hloc.volume_left);
                v.put("volume_right", hloc.volume_right);
                v.put("avg", hloc.avg);
                ret.add(v);
            }

            return ret;
        }
    }
    private static class VolumesQuery extends GraphQLQuery {
        private static class Volume {
            long period_start;
            String periodStartDateTime;
            String volume;
            int num_trades;
        }
        private static final String volumeQuery = "query Volumes($market: MarketPair "+
                "$interval: Interval) { " +
                "volumes(market: $market interval: $interval) " +
                "{ period_start: periodStart periodStartDateTime volume: formattedVolume num_trades: numTrades } }";
        private final String query = volumeQuery;

        private final Map<String,String> variables;

        VolumesQuery(Map<String,String> params){
            String interval = params.get("interval");
            if (interval != null) {
                params.put("interval", interval.toUpperCase());
            }
            variables = params;
        }

        @Override
        protected Object translateSuccessfulResponse(String response) {
            GraphQLResponse<List<Volume>> res = gson.fromJson(response,new TypeToken<GraphQLResponse<List<Volume>>>(){}.getType());
            List<Map<String,Object>> ret = new ArrayList<>();
            for (Volume volume : res.getData()) {
                Map<String,Object> v = new HashMap<>();
                String timestamp = variables.get("timestamp");
                if (timestamp != null && timestamp.equals("no")) {
                    v.put("period_start", volume.periodStartDateTime);
                } else {
                    v.put("period_start", volume.period_start);
                }
                v.put("num_trades", volume.num_trades);
                v.put("volume", volume.volume);
                ret.add(v);
            }

            return ret;
        }
    }
}
