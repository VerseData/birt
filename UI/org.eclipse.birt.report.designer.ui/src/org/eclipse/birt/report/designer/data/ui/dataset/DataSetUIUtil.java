/*******************************************************************************
 * Copyright (c) 2004, 2005 Actuate Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *  Actuate Corporation  - initial API and implementation
 *******************************************************************************/
package org.eclipse.birt.report.designer.data.ui.dataset;

import java.util.logging.Logger;

import org.eclipse.birt.core.data.DataType;
import org.eclipse.birt.core.exception.BirtException;
import org.eclipse.birt.data.engine.api.DataEngineContext;
import org.eclipse.birt.report.data.adapter.api.DataRequestSession;
import org.eclipse.birt.report.data.adapter.api.DataSessionContext;
import org.eclipse.birt.report.model.api.CachedMetaDataHandle;
import org.eclipse.birt.report.model.api.DataSetHandle;
import org.eclipse.birt.report.model.api.activity.SemanticException;
import org.eclipse.birt.report.model.api.elements.DesignChoiceConstants;

/**
 * The utility class.
 */
public final class DataSetUIUtil
{
	// logger instance
	private static Logger logger = Logger.getLogger( DataSetUIUtil.class.getName( ) );
	
	/**
	 * Save the column meta data to data set handle.
	 * 
	 * @param dataSetHandle
	 * @param items
	 * @throws SemanticException
	 */
	public static void updateColumnCache( DataSetHandle dataSetHandle )
			throws SemanticException
	{
		DataSessionContext context = null;
		try
		{
			context = new DataSessionContext( DataEngineContext.DIRECT_PRESENTATION,
					dataSetHandle.getRoot( ),
					null );
			DataRequestSession drSession = DataRequestSession.newSession( context );
			drSession.refreshMetaData( dataSetHandle );
		}
		catch ( BirtException e )
		{
			logger.entering( DataSetUIUtil.class.getName( ),
					"updateColumnCache",
					new Object[]{
						e
					} );
		}
	}
	
	/**
	 * Add this method according to GUI's requirement.This method is only for temporarily usage.
	 * @param dataSetHandle
	 * @return
	 * @throws SemanticException
	 * @deprecated
	 */
	public static CachedMetaDataHandle getCachedMetaDataHandle( DataSetHandle dataSetHandle ) throws SemanticException
	{
		if( dataSetHandle.getCachedMetaDataHandle( ) == null )
		{
			updateColumnCache( dataSetHandle );
		}
		
		return dataSetHandle.getCachedMetaDataHandle( );
	}
	
	/**
	 * Map oda data type to model data type.
	 * 
	 * @param modelDataType
	 * @return
	 */
	private static String toModelDataType( int modelDataType )
	{
		if ( modelDataType == DataType.INTEGER_TYPE )
			return DesignChoiceConstants.COLUMN_DATA_TYPE_INTEGER ;
		else if ( modelDataType == DataType.STRING_TYPE )
			return DesignChoiceConstants.COLUMN_DATA_TYPE_STRING;
		else if ( modelDataType == DataType.DATE_TYPE )
			return DesignChoiceConstants.COLUMN_DATA_TYPE_DATETIME;
		else if ( modelDataType == DataType.DECIMAL_TYPE )
			return DesignChoiceConstants.COLUMN_DATA_TYPE_DECIMAL;
		else if ( modelDataType == DataType.DOUBLE_TYPE )
			return DesignChoiceConstants.COLUMN_DATA_TYPE_FLOAT;
		
		return DesignChoiceConstants.COLUMN_DATA_TYPE_ANY;
	}
}
