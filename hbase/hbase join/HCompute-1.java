import java.io.IOException;
import java.util.ArrayList;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.filter.PrefixFilter;
import org.apache.hadoop.hbase.io.ImmutableBytesWritable;
import org.apache.hadoop.hbase.mapreduce.TableInputFormat;
import org.apache.hadoop.hbase.mapreduce.TableMapReduceUtil;
import org.apache.hadoop.hbase.mapreduce.TableMapper;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.Reducer.Context;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;
import org.apache.hadoop.mapreduce.lib.input.MultipleInputs;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;

import au.com.bytecode.opencsv.CSVParser;

public class HCompute {
	public static class HComputerMapper extends TableMapper<Text, Text> {
		HTableDescriptor desc;

		public void map(ImmutableBytesWritable row, Result values,
				Context context) {
			try {
				ImmutableBytesWritable keyData = new ImmutableBytesWritable(
						row.get(), 0, Bytes.SIZEOF_INT);
				String keyUTF8 = new String(keyData.get(), "UTF-8");
				String value_to_be_written = new String(values.getColumnLatest(
						Bytes.toBytes("MovieData"), Bytes.toBytes("value"))
						.getValue(), "UTF-8");
				String[] content = value_to_be_written.split(";");
				String movieId = content[1];
				String movieName = content[0];
				System.out.println(content[0] + ";" + content[1]);
				context.write(new Text(movieId), new Text(movieName +";F1"));
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	public static class AverageMapper extends
			Mapper<Object, Text, Text, Text> {
		public void map(Object key, Text value, Context context)
				throws IOException, InterruptedException {

			// CSV Parser
			CSVParser csv = new CSVParser(',');
			String[] content = csv.parseLine(value.toString());

			// Start year and month
			String movieId = content[0];
			String rating = content[2];

			context.write(new Text(movieId),
					new Text(rating + ";F2"));
		}
	}

	// Defining the reducer class
	public static class AverageReducer extends
	Reducer<Text, Text, Text, Text> {

		public void reduce(Text key, Iterable<Text> values, Context context)
				throws IOException, InterruptedException {
			ArrayList<Integer> ratings = new ArrayList<Integer>();
			String name = null;
			double sum;
			double count;
			for (Text v : values) {
				String Value = v.toString();
				String[] content = Value.split(";");
				if (content[1].equals("F1")) {
					name = content[0];
				}
				if (content[1].equals("F2")) {
					ratings.add(Integer.parseInt(content[0]));
				}
			}
			sum = 0;
			count = 0;
			for (int i = 0; i < ratings.size(); i++) {
				sum += ratings.get(i);
				count += 1;
			}
			double avg = sum / count;
			String output = avg + "";
			context.write(new Text(name), new Text(output));
		}
	}
	public static void main(String args[]) throws IOException,
			ClassNotFoundException, InterruptedException {
		Configuration conf = new Configuration();
		if (args.length != 3) {
			System.err.println("Usage: HCompute <in1> <in2> <out>");
			System.exit(2);
		}
		Job job = new Job(conf, "HCompute");
		job.setJarByClass(HCompute.class);
		
		
		byte[] criteria = "movie".getBytes();
		PrefixFilter pf = new PrefixFilter(criteria);
		Scan sc = new Scan(criteria);
		sc.setFilter(pf);
		sc.setCaching(1000);
		sc.setCacheBlocks(false);
		TableMapReduceUtil.initTableMapperJob("DataAll", sc,
				HComputerMapper.class, Text.class, Text.class, job);
		
		//job.setMapperClass(HComputerMapper.class);
		MultipleInputs.addInputPath(job, new Path(args[0]), TableInputFormat.class,HComputerMapper.class);
		MultipleInputs.addInputPath(job, new Path(args[1]), TextInputFormat.class,AverageMapper.class);
		job.setReducerClass(AverageReducer.class);
		job.setOutputFormatClass(TextOutputFormat.class);
		FileOutputFormat.setOutputPath(job, new Path(args[2]));
		System.exit(job.waitForCompletion(true) ? 0 : 1);

	}
}
