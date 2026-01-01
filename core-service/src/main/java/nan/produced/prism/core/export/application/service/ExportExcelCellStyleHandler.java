package nan.produced.prism.core.export.application.service;

import com.alibaba.excel.write.handler.CellWriteHandler;
import com.alibaba.excel.write.metadata.holder.WriteSheetHolder;
import com.alibaba.excel.write.metadata.holder.WriteTableHolder;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import nan.produced.prism.core.export.api.dto.ExportValueType;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.Workbook;

final class ExportExcelCellStyleHandler implements CellWriteHandler {

    private static final String EXCEL_DATETIME_FORMAT = "yyyy-mm-dd hh:mm:ss";

    private final List<ExportValueType> typesByColumn;

    private volatile boolean initialized;
    private final Map<ExportValueType, CellStyle> styleCache = new EnumMap<>(ExportValueType.class);

    ExportExcelCellStyleHandler(List<ExportValueType> typesByColumn) {
        this.typesByColumn = typesByColumn;
    }

    @Override
    public void afterCellDispose(WriteSheetHolder writeSheetHolder,
                                 WriteTableHolder writeTableHolder,
                                 List<com.alibaba.excel.metadata.data.WriteCellData<?>> cellDataList,
                                 Cell cell,
                                 com.alibaba.excel.metadata.Head head,
                                 Integer relativeRowIndex,
                                 Boolean isHead) {
        if (cell == null || Boolean.TRUE.equals(isHead)) {
            return;
        }
        if (typesByColumn == null || cell.getColumnIndex() < 0 || cell.getColumnIndex() >= typesByColumn.size()) {
            return;
        }
        ExportValueType type = typesByColumn.get(cell.getColumnIndex());
        if (type == null) {
            return;
        }
        Workbook workbook = writeSheetHolder != null ? writeSheetHolder.getSheet().getWorkbook() : null;
        if (workbook == null) {
            return;
        }
        if (!initialized) {
            synchronized (styleCache) {
                if (!initialized) {
                    initStyles(workbook);
                    initialized = true;
                }
            }
        }
        CellStyle style = styleCache.get(type);
        if (style != null) {
            cell.setCellStyle(style);
        }
    }

    private void initStyles(Workbook workbook) {
        DataFormat fmt = workbook.createDataFormat();

        CellStyle text = workbook.createCellStyle();
        text.setDataFormat(fmt.getFormat("@"));
        styleCache.put(ExportValueType.TEXT, text);
        styleCache.put(ExportValueType.JSON, text);

        CellStyle intStyle = workbook.createCellStyle();
        intStyle.setDataFormat(fmt.getFormat("0"));
        styleCache.put(ExportValueType.INT, intStyle);
        styleCache.put(ExportValueType.LONG, intStyle);

        CellStyle doubleStyle = workbook.createCellStyle();
        doubleStyle.setDataFormat(fmt.getFormat("0.00"));
        styleCache.put(ExportValueType.DOUBLE, doubleStyle);

        CellStyle dateTime = workbook.createCellStyle();
        dateTime.setDataFormat(fmt.getFormat(EXCEL_DATETIME_FORMAT));
        styleCache.put(ExportValueType.DATETIME, dateTime);

        // BOOLEAN 默认 General（保留原生 true/false）
    }
}

