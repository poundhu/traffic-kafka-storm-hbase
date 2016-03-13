package com.nano;

import backtype.storm.Config;
import backtype.storm.LocalCluster;
import backtype.storm.StormSubmitter;
import backtype.storm.spout.SchemeAsMultiScheme;
import backtype.storm.topology.TopologyBuilder;
import backtype.storm.tuple.Fields;
import com.nano.bolts.HBaseBolt;
import com.nano.bolts.MessageSplitBolt;
import com.nano.bolts.ProcessingBolt;
import storm.kafka.*;

import java.util.Arrays;

/**
 * Created by Administrator on 2016/3/8.
 */
public class Application {

    public static void main(String... args) throws Exception {
        String zks = "zookeeper1:2181,zookeeper2:2181,zookeeper3:2181";
        String topic = "traffic-info-topic";
        String zkRoot = "/storm"; // default zookeeper root configuration for storm
        String id = "word";

        BrokerHosts brokerHosts = new ZkHosts(zks);
        SpoutConfig spoutConf = new SpoutConfig(brokerHosts, topic, zkRoot, id);
        spoutConf.scheme = new SchemeAsMultiScheme(new StringScheme());
        spoutConf.zkServers = Arrays.asList(new String[] {"zookeeper1", "zookeeper2", "zookeeper3"});
        spoutConf.zkPort = 2181;

        TopologyBuilder builder = new TopologyBuilder();
        builder.setSpout("reader",new KafkaSpout(spoutConf),15);
        builder.setBolt("splitter",new MessageSplitBolt(),15).shuffleGrouping("reader");
        builder.setBolt("processor",new ProcessingBolt(),15).fieldsGrouping("splitter",new Fields("area")); // 相同区域交由同一个bolt处理
        builder.setBolt("writer",new HBaseBolt(),15).fieldsGrouping("processor",new Fields("row")); // 相同key交由同一个bolt处理

        Config conf = new Config();
        String name = Application.class.getSimpleName();
        if (args != null && args.length > 0) {
            // Nimbus host name passed from command line
            conf.put(Config.NIMBUS_HOST, args[0]);
            conf.setNumWorkers(100);
            StormSubmitter.submitTopologyWithProgressBar(name, conf, builder.createTopology());
        } else {
            System.setProperty("hadoop.home.dir", "D:/hadoop");
            conf.setMaxTaskParallelism(3);
            LocalCluster cluster = new LocalCluster();
            cluster.submitTopology(Application.class.getSimpleName(), conf, builder.createTopology());
        }
    }
}
