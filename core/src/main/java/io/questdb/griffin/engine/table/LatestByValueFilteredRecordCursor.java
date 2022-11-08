/*******************************************************************************
 *     ___                  _   ____  ____
 *    / _ \ _   _  ___  ___| |_|  _ \| __ )
 *   | | | | | | |/ _ \/ __| __| | | |  _ \
 *   | |_| | |_| |  __/\__ \ |_| |_| | |_) |
 *    \__\_\\__,_|\___||___/\__|____/|____/
 *
 *  Copyright (c) 2014-2019 Appsicle
 *  Copyright (c) 2019-2022 QuestDB
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ******************************************************************************/

package io.questdb.griffin.engine.table;

import io.questdb.cairo.sql.DataFrame;
import io.questdb.cairo.sql.DataFrameCursor;
import io.questdb.cairo.sql.Function;
import io.questdb.cairo.sql.SqlExecutionCircuitBreaker;
import io.questdb.griffin.PlanSink;
import io.questdb.griffin.Plannable;
import io.questdb.griffin.SqlException;
import io.questdb.griffin.SqlExecutionContext;
import io.questdb.std.IntList;
import org.jetbrains.annotations.NotNull;

class LatestByValueFilteredRecordCursor extends AbstractDataFrameRecordCursor implements Plannable {

    private final int columnIndex;
    private final Function filter;
    private final int symbolKey;
    private boolean empty;
    private boolean hasNext;

    public LatestByValueFilteredRecordCursor(
            int columnIndex,
            int symbolKey,
            @NotNull Function filter,
            @NotNull IntList columnIndexes
    ) {
        super(columnIndexes);
        this.columnIndex = columnIndex;
        this.symbolKey = symbolKey;
        this.filter = filter;
    }

    @Override
    public void close() {
        dataFrameCursor.close();
    }

    @Override
    public boolean hasNext() {
        if (hasNext) {
            hasNext = false;
            return true;
        }
        return false;
    }

    @Override
    public long size() {
        return -1;
    }

    @Override
    public void toPlan(PlanSink sink) {
        sink.type("Row backward scan");
        sink.attr("symbolFilter").putColumnName(columnIndex).put('=').put(symbolKey);
        sink.attr("filter").val(filter);
    }

    @Override
    public void toTop() {
        hasNext = !empty;
        filter.toTop();
    }

    private void findRecord(SqlExecutionContext executionContext) {
        SqlExecutionCircuitBreaker circuitBreaker = executionContext.getCircuitBreaker();
        empty = true;
        DataFrame frame;
        OUT:
        while ((frame = this.dataFrameCursor.next()) != null) {
            final long rowLo = frame.getRowLo();
            final long rowHi = frame.getRowHi() - 1;

            recordA.jumpTo(frame.getPartitionIndex(), rowHi);
            for (long row = rowHi; row >= rowLo; row--) {
                circuitBreaker.statefulThrowExceptionIfTripped();
                recordA.setRecordIndex(row);
                if (filter.getBool(recordA)) {
                    int key = recordA.getInt(columnIndex);
                    if (key == symbolKey) {
                        empty = false;
                        break OUT;
                    }
                }
            }
        }
    }

    @Override
    void of(DataFrameCursor dataFrameCursor, SqlExecutionContext executionContext) throws SqlException {
        this.dataFrameCursor = dataFrameCursor;
        this.recordA.of(dataFrameCursor.getTableReader());
        this.recordB.of(dataFrameCursor.getTableReader());
        filter.init(this, executionContext);
        findRecord(executionContext);
        hasNext = !empty;
    }
}
