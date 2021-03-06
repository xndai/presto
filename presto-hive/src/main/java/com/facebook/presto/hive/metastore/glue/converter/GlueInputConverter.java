/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.facebook.presto.hive.metastore.glue.converter;

import com.amazonaws.services.glue.model.DatabaseInput;
import com.amazonaws.services.glue.model.PartitionInput;
import com.amazonaws.services.glue.model.SerDeInfo;
import com.amazonaws.services.glue.model.StorageDescriptor;
import com.amazonaws.services.glue.model.TableInput;
import com.facebook.presto.hive.metastore.Column;
import com.facebook.presto.hive.metastore.Database;
import com.facebook.presto.hive.metastore.Partition;
import com.facebook.presto.hive.metastore.Storage;
import com.facebook.presto.hive.metastore.Table;
import com.google.common.collect.ImmutableMap;

import java.util.List;

import static java.util.stream.Collectors.toList;

public final class GlueInputConverter
{
    private GlueInputConverter() {}

    public static DatabaseInput convertDatabase(Database database)
    {
        DatabaseInput input = new DatabaseInput();
        input.setName(database.getDatabaseName());
        input.setParameters(database.getParameters());
        database.getComment().ifPresent(input::setDescription);
        database.getLocation().ifPresent(input::setLocationUri);
        return input;
    }

    public static TableInput convertTable(Table table)
    {
        TableInput input = new TableInput();
        input.setName(table.getTableName());
        input.setOwner(table.getOwner());
        input.setTableType(table.getTableType());
        input.setStorageDescriptor(convertStorage(table.getStorage(), table.getDataColumns()));
        input.setPartitionKeys(table.getPartitionColumns().stream().map(GlueInputConverter::convertColumn).collect(toList()));
        input.setParameters(table.getParameters());
        table.getViewOriginalText().ifPresent(input::setViewOriginalText);
        table.getViewExpandedText().ifPresent(input::setViewExpandedText);
        return input;
    }

    public static PartitionInput convertPartition(Partition partition)
    {
        PartitionInput input = new PartitionInput();
        input.setValues(partition.getValues());
        input.setStorageDescriptor(convertStorage(partition.getStorage(), partition.getColumns()));
        input.setParameters(partition.getParameters());
        return input;
    }

    private static StorageDescriptor convertStorage(Storage storage, List<Column> columns)
    {
        if (storage.isSorted() || storage.isSkewed()) {
            throw new IllegalArgumentException("Writing to sorted and/or skewed table/partition is not supported");
        }
        SerDeInfo serdeInfo = new SerDeInfo()
                .withSerializationLibrary(storage.getStorageFormat().getSerDeNullable())
                .withParameters(storage.getSerdeParameters());

        StorageDescriptor sd = new StorageDescriptor();
        sd.setLocation(storage.getLocation());
        sd.setColumns(columns.stream().map(GlueInputConverter::convertColumn).collect(toList()));
        sd.setSerdeInfo(serdeInfo);
        sd.setInputFormat(storage.getStorageFormat().getInputFormatNullable());
        sd.setOutputFormat(storage.getStorageFormat().getOutputFormatNullable());
        sd.setParameters(ImmutableMap.of());

        if (storage.getBucketProperty().isPresent()) {
            sd.setNumberOfBuckets(storage.getBucketProperty().get().getBucketCount());
            sd.setBucketColumns(storage.getBucketProperty().get().getBucketedBy());
        }

        return sd;
    }

    private static com.amazonaws.services.glue.model.Column convertColumn(Column prestoColumn)
    {
        return new com.amazonaws.services.glue.model.Column()
                .withName(prestoColumn.getName())
                .withType(prestoColumn.getType().toString())
                .withComment(prestoColumn.getComment().orElse(null));
    }
}
