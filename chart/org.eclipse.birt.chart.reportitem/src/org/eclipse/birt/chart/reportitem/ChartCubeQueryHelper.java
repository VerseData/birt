/*******************************************************************************
 * Copyright (c) 2007 Actuate Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *  Actuate Corporation  - initial API and implementation
 *******************************************************************************/

package org.eclipse.birt.chart.reportitem;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.birt.chart.exception.ChartException;
import org.eclipse.birt.chart.log.ILogger;
import org.eclipse.birt.chart.log.Logger;
import org.eclipse.birt.chart.model.Chart;
import org.eclipse.birt.chart.model.ChartWithAxes;
import org.eclipse.birt.chart.model.ChartWithoutAxes;
import org.eclipse.birt.chart.model.attribute.SortOption;
import org.eclipse.birt.chart.model.component.Axis;
import org.eclipse.birt.chart.model.data.Query;
import org.eclipse.birt.chart.model.data.SeriesDefinition;
import org.eclipse.birt.chart.reportitem.i18n.Messages;
import org.eclipse.birt.chart.reportitem.plugin.ChartReportItemPlugin;
import org.eclipse.birt.core.data.ExpressionUtil;
import org.eclipse.birt.core.exception.BirtException;
import org.eclipse.birt.data.aggregation.IBuildInAggregation;
import org.eclipse.birt.data.engine.api.IDataQueryDefinition;
import org.eclipse.birt.data.engine.api.ISortDefinition;
import org.eclipse.birt.data.engine.api.querydefn.Binding;
import org.eclipse.birt.data.engine.api.querydefn.ConditionalExpression;
import org.eclipse.birt.data.engine.api.querydefn.ScriptExpression;
import org.eclipse.birt.data.engine.olap.api.query.IBaseCubeQueryDefinition;
import org.eclipse.birt.data.engine.olap.api.query.ICubeFilterDefinition;
import org.eclipse.birt.data.engine.olap.api.query.ICubeQueryDefinition;
import org.eclipse.birt.data.engine.olap.api.query.ICubeSortDefinition;
import org.eclipse.birt.data.engine.olap.api.query.IDimensionDefinition;
import org.eclipse.birt.data.engine.olap.api.query.IEdgeDefinition;
import org.eclipse.birt.data.engine.olap.api.query.IHierarchyDefinition;
import org.eclipse.birt.data.engine.olap.api.query.ILevelDefinition;
import org.eclipse.birt.data.engine.olap.api.query.IMeasureDefinition;
import org.eclipse.birt.data.engine.olap.api.query.ISubCubeQueryDefinition;
import org.eclipse.birt.report.data.adapter.api.DataAdapterUtil;
import org.eclipse.birt.report.item.crosstab.core.ICrosstabConstants;
import org.eclipse.birt.report.item.crosstab.core.de.AggregationCellHandle;
import org.eclipse.birt.report.item.crosstab.core.de.CrosstabReportItemHandle;
import org.eclipse.birt.report.item.crosstab.core.de.CrosstabViewHandle;
import org.eclipse.birt.report.item.crosstab.core.de.DimensionViewHandle;
import org.eclipse.birt.report.item.crosstab.core.de.LevelViewHandle;
import org.eclipse.birt.report.item.crosstab.core.de.MeasureViewHandle;
import org.eclipse.birt.report.model.api.ComputedColumnHandle;
import org.eclipse.birt.report.model.api.DesignElementHandle;
import org.eclipse.birt.report.model.api.ExtendedItemHandle;
import org.eclipse.birt.report.model.api.FilterConditionElementHandle;
import org.eclipse.birt.report.model.api.MemberValueHandle;
import org.eclipse.birt.report.model.api.ModuleUtil;
import org.eclipse.birt.report.model.api.MultiViewsHandle;
import org.eclipse.birt.report.model.api.StructureFactory;
import org.eclipse.birt.report.model.api.extension.ExtendedElementException;
import org.eclipse.birt.report.model.api.olap.CubeHandle;
import org.eclipse.birt.report.model.api.olap.HierarchyHandle;
import org.eclipse.birt.report.model.api.olap.LevelHandle;
import org.eclipse.birt.report.model.api.util.CubeUtil;
import org.eclipse.birt.report.model.elements.interfaces.IMemberValueModel;
import org.eclipse.emf.common.util.EList;

/**
 * Query helper for cube query definition
 */

public class ChartCubeQueryHelper
{

	private static ILogger logger = Logger.getLogger( "org.eclipse.birt.chart.reportitem/trace" ); //$NON-NLS-1$

	private final ExtendedItemHandle handle;
	private final Chart cm;

	/**
	 * Maps for registered column bindings.<br>
	 * Key: binding name, value: Binding
	 */
	private Map<String, Binding> registeredBindings = new HashMap<String, Binding>( );
	/**
	 * Maps for registered queries.<br>
	 * Key: binding name, value: raw query expression
	 */
	private Map<String, String> registeredQueries = new HashMap<String, String>( );

	/**
	 * Maps for registered level definitions.<br>
	 * Key: Binding name of query, value: ILevelDefinition
	 */
	private Map<String, ILevelDefinition> registeredLevels = new HashMap<String, ILevelDefinition>( );

	/**
	 * Maps for registered measure definitions.<br>
	 * Key: Binding name of query, value: IMeasureDefinition
	 */
	private Map<String, IMeasureDefinition> registeredMeasures = new HashMap<String, IMeasureDefinition>( );

	/**
	 * Maps for registered level handles.<br>
	 * Key: LevelHandle, value: ILevelDefinition
	 */
	private Map<LevelHandle, ILevelDefinition> registeredLevelHandles = new HashMap<LevelHandle, ILevelDefinition>( );

	private String rowEdgeDimension;

	/**
	 * Indicates if used for Live preview in chart builder. In this case,
	 * sub/nest query is not supported, and aggregateOn in measure binding
	 * should be removed to make preview work.
	 */
	private boolean bLivePreview = false;

	public ChartCubeQueryHelper( ExtendedItemHandle handle, Chart cm )
	{
		this.handle = handle;
		this.cm = cm;
	}

	/**
	 * Creates the cube query definition for chart. If parent definition is
	 * null, it's usually used for Live preview in chart builder. If chart in
	 * xtab, will return sub cube query definition.
	 * 
	 * @param parent
	 * @return ICubeQueryDefinition for cube consuming or
	 *         ISubCubeQueryDefinition for chart in xtab case
	 * @throws BirtException
	 */
	public IBaseCubeQueryDefinition createCubeQuery( IDataQueryDefinition parent )
			throws BirtException
	{
		bLivePreview = parent == null;

		CubeHandle cubeHandle = handle.getCube( );
		ICubeQueryDefinition cubeQuery = null;
		if ( cubeHandle == null )
		{
			// Create sub query for chart in xtab
			cubeHandle = ChartXTabUtil.getBindingCube( handle );
			if ( cubeHandle == null )
			{
				throw new ChartException( ChartReportItemPlugin.ID,
						ChartException.NULL_DATASET,
						Messages.getString( "ChartCubeQueryHelper.Error.MustBindCube" ) ); //$NON-NLS-1$
			}

			// Do not support sub query in Live preview.
			if ( !bLivePreview )
			{
				ISubCubeQueryDefinition subQuery = createSubCubeQuery( );
				if ( subQuery != null )
				{
					// Adds min and max binding to parent query definition for
					// shared scale.
					// TODO blocked by DTE for multiple aggregations
					// addMinMaxBinding( parent );
					return subQuery;
				}
			}
		}

		cubeQuery = ChartXTabUtil.getCubeElementFactory( )
				.createCubeQuery( cubeHandle.getQualifiedName( ) );

		// Add column bindings from handle
		initBindings( cubeQuery, cubeHandle );

		List sdList = getAllSeriesDefinitions( cm );

		// Add measures and dimensions
		for ( int i = 0; i < sdList.size( ); i++ )
		{
			SeriesDefinition sd = (SeriesDefinition) sdList.get( i );
			List queryList = sd.getDesignTimeSeries( ).getDataDefinition( );
			for ( int j = 0; j < queryList.size( ); j++ )
			{
				Query query = (Query) queryList.get( j );
				// Add measures or dimensions for data definition, and update
				// query expression
				bindSeriesQuery( query.getDefinition( ), cubeQuery, cubeHandle );
			}

			// Add measures or dimensions for optional grouping, and update
			// query expression
			bindSeriesQuery( sd.getQuery( ).getDefinition( ),
					cubeQuery,
					cubeHandle );
		}

		// Add aggregation list to measure bindings on demand
		Collection levelsInOrder = getAllLevelsInHierarchyOrder( cubeHandle,
				cubeQuery );
		for ( Iterator measureNames = registeredMeasures.keySet( ).iterator( ); measureNames.hasNext( ); )
		{
			Binding binding = registeredBindings.get( measureNames.next( ) );
			if ( binding != null && binding.getAggregatOns( ).isEmpty( ) )
			{
				for ( Iterator levels = levelsInOrder.iterator( ); levels.hasNext( ); )
				{
					ILevelDefinition level = (ILevelDefinition) levels.next( );
					String dimensionName = level.getHierarchy( )
							.getDimension( )
							.getName( );
					binding.addAggregateOn( ExpressionUtil.createJSDimensionExpression( dimensionName,
							level.getName( ) ) );
				}
			}
		}

		// Add sorting
		// Sorting must be added after measures and dimensions, since sort
		// key references to measures or dimensions
		for ( int i = 0; i < sdList.size( ); i++ )
		{
			SeriesDefinition sd = (SeriesDefinition) sdList.get( i );
			addSorting( cubeQuery, cubeHandle, sd, i );
		}

		// Add filter
		addCubeFilter( cubeQuery );

		return cubeQuery;
	}

	private ISubCubeQueryDefinition createSubCubeQuery( ) throws BirtException
	{
		String queryName = ChartReportItemConstants.CHART_SUBQUERY;
		AggregationCellHandle containerCell = ChartXTabUtil.getXtabContainerCell( handle );
		if ( containerCell == null )
		{
			return null;
		}
		CrosstabReportItemHandle xtab = containerCell.getCrosstab( );
		int columnLevelCount = ChartXTabUtil.getLevelCount( xtab,
				ICrosstabConstants.COLUMN_AXIS_TYPE );
		int rowLevelCount = ChartXTabUtil.getLevelCount( xtab,
				ICrosstabConstants.ROW_AXIS_TYPE );
		if ( cm instanceof ChartWithAxes )
		{
			if ( ( (ChartWithAxes) cm ).isTransposed( ) )
			{
				if ( columnLevelCount >= 1 )
				{
					ISubCubeQueryDefinition subCubeQuery = ChartXTabUtil.getCubeElementFactory( )
							.createSubCubeQuery( queryName );
					subCubeQuery.setStartingLevelOnColumn( ChartXTabUtil.createDimensionExpression( ChartXTabUtil.getLevel( xtab,
							ICrosstabConstants.COLUMN_AXIS_TYPE,
							columnLevelCount - 1 )
							.getCubeLevel( ) ) );
					if ( rowLevelCount > 1 )
					{
						// Only add another level in multiple levels case
						subCubeQuery.setStartingLevelOnRow( ChartXTabUtil.createDimensionExpression( ChartXTabUtil.getLevel( xtab,
								ICrosstabConstants.ROW_AXIS_TYPE,
								rowLevelCount - 2 )
								.getCubeLevel( ) ) );
					}
					return subCubeQuery;
				}
				else if ( rowLevelCount > 1 )
				{
					// No column level and multiple row levels, use the top
					// row level
					ISubCubeQueryDefinition subCubeQuery = ChartXTabUtil.getCubeElementFactory( )
							.createSubCubeQuery( queryName );
					subCubeQuery.setStartingLevelOnRow( ChartXTabUtil.createDimensionExpression( ChartXTabUtil.getLevel( xtab,
							ICrosstabConstants.ROW_AXIS_TYPE,
							rowLevelCount - 2 )
							.getCubeLevel( ) ) );
					return subCubeQuery;
				}
				// If corresponding column is null and without multiple
				// levels, do not use sub query
			}
			else
			{
				if ( rowLevelCount >= 1 )
				{
					ISubCubeQueryDefinition subCubeQuery = ChartXTabUtil.getCubeElementFactory( )
							.createSubCubeQuery( queryName );
					subCubeQuery.setStartingLevelOnRow( ChartXTabUtil.createDimensionExpression( ChartXTabUtil.getLevel( xtab,
							ICrosstabConstants.ROW_AXIS_TYPE,
							rowLevelCount - 1 )
							.getCubeLevel( ) ) );
					if ( columnLevelCount > 1 )
					{
						// Only add another level in multiple levels case
						subCubeQuery.setStartingLevelOnColumn( ChartXTabUtil.createDimensionExpression( ChartXTabUtil.getLevel( xtab,
								ICrosstabConstants.COLUMN_AXIS_TYPE,
								columnLevelCount - 2 )
								.getCubeLevel( ) ) );
					}
					return subCubeQuery;
				}
				else if ( columnLevelCount > 1 )
				{
					// No row level and multiple column levels, use the top
					// column level
					ISubCubeQueryDefinition subCubeQuery = ChartXTabUtil.getCubeElementFactory( )
							.createSubCubeQuery( queryName );
					subCubeQuery.setStartingLevelOnColumn( ChartXTabUtil.createDimensionExpression( ChartXTabUtil.getLevel( xtab,
							ICrosstabConstants.COLUMN_AXIS_TYPE,
							columnLevelCount - 2 )
							.getCubeLevel( ) ) );
					return subCubeQuery;
				}
				// If corresponding row is null and without multiple levels,
				// do not use sub query
			}
		}

		// Do not use sub query for other cases
		return null;
	}

	/**
	 * Adds min and max binding to parent query definition
	 * 
	 * @param parent
	 * @throws BirtException
	 */
	private void addMinMaxBinding( IDataQueryDefinition parent )
			throws BirtException
	{
		SeriesDefinition sdValue = (SeriesDefinition) ( (ChartWithAxes) cm ).getOrthogonalAxes( ( (ChartWithAxes) cm ).getBaseAxes( )[0],
				true )[0].getSeriesDefinitions( ).get( 0 );
		Query query = (Query) sdValue.getDesignTimeSeries( )
				.getDataDefinition( )
				.get( 0 );
		String bindingName = ChartXTabUtil.getBindingName( query.getDefinition( ),
				true );
		for ( Iterator bindings = ChartReportItemUtil.getAllColumnBindingsIterator( handle ); bindings.hasNext( ); )
		{
			ComputedColumnHandle column = (ComputedColumnHandle) bindings.next( );
			if ( column.getName( ).equals( bindingName ) )
			{
				// Create max binding
				Binding binding = new Binding( ChartReportItemConstants.QUERY_MAX );
				binding.setDataType( DataAdapterUtil.adaptModelDataType( column.getDataType( ) ) );
				binding.setExpression( new ScriptExpression( column.getExpression( ) ) );
				binding.setAggrFunction( IBuildInAggregation.TOTAL_MAX_FUNC );
				addAggregateOn( binding,
						column.getAggregateOnList( ),
						null,
						null );
				( (ICubeQueryDefinition) parent ).addBinding( binding );

				// Create min binding
				binding = new Binding( ChartReportItemConstants.QUERY_MIN );
				binding.setDataType( DataAdapterUtil.adaptModelDataType( column.getDataType( ) ) );
				binding.setExpression( new ScriptExpression( column.getExpression( ) ) );
				binding.setAggrFunction( IBuildInAggregation.TOTAL_MIN_FUNC );
				addAggregateOn( binding,
						column.getAggregateOnList( ),
						null,
						null );
				( (ICubeQueryDefinition) parent ).addBinding( binding );

				break;
			}

		}
	}

	private void initBindings( ICubeQueryDefinition cubeQuery, CubeHandle cube )
			throws BirtException
	{
		for ( Iterator bindings = ChartReportItemUtil.getAllColumnBindingsIterator( handle ); bindings.hasNext( ); )
		{
			ComputedColumnHandle column = (ComputedColumnHandle) bindings.next( );
			// Create new binding
			Binding binding = new Binding( column.getName( ) );
			binding.setDataType( DataAdapterUtil.adaptModelDataType( column.getDataType( ) ) );
			binding.setExpression( new ScriptExpression( column.getExpression( ) ) );
			binding.setAggrFunction( column.getAggregateFunction( ) == null
					? null
					: DataAdapterUtil.adaptModelAggregationType( column.getAggregateFunction( ) ) );

			List lstAggOn = column.getAggregateOnList( );

			// Do not add aggregateOn to binding in Live preview case, because
			// it doesn't use sub query.
			if ( !bLivePreview && !lstAggOn.isEmpty( ) )
			{
				// Add aggregate on in binding
				addAggregateOn( binding, lstAggOn, cubeQuery, cube );
			}

			// Add binding query expression here
			registeredBindings.put( column.getName( ), binding );
			// Add raw query expression here
			registeredQueries.put( binding.getBindingName( ),
					column.getExpression( ) );

			// Do not add every binding to cube query, since it may be not used.
			// The binding will be added when found in chart.
		}
	}

	private void addAggregateOn( Binding binding, List lstAggOn,
			ICubeQueryDefinition cubeQuery, CubeHandle cube )
			throws BirtException
	{
		for ( Iterator iAggs = lstAggOn.iterator( ); iAggs.hasNext( ); )
		{
			String aggOn = (String) iAggs.next( );
			// Convert full level name to dimension expression
			String[] levelNames = CubeUtil.splitLevelName( aggOn );
			String dimExpr = ExpressionUtil.createJSDimensionExpression( levelNames[0],
					levelNames[1] );
			binding.addAggregateOn( dimExpr );
		}
	}

	private void addSorting( ICubeQueryDefinition cubeQuery, CubeHandle cube,
			SeriesDefinition sd, int i ) throws BirtException
	{
		if ( sd.getSortKey( ) == null )
		{
			return;
		}

		String sortKey = sd.getSortKey( ).getDefinition( );
		if ( sd.isSetSorting( ) && sortKey != null && sortKey.length( ) > 0 )
		{
			String sortKeyBinding = ChartXTabUtil.getBindingName( sd.getSortKey( )
					.getDefinition( ),
					true );
			if ( registeredLevels.containsKey( sortKeyBinding ) )
			{
				// Add sorting on dimension
				ICubeSortDefinition sortDef = ChartXTabUtil.getCubeElementFactory( )
						.createCubeSortDefinition( sortKey,
								registeredLevels.get( sortKeyBinding ),
								null,
								null,
								sd.getSorting( ) == SortOption.ASCENDING_LITERAL
										? ISortDefinition.SORT_ASC
										: ISortDefinition.SORT_DESC );
				cubeQuery.addSort( sortDef );
			}
			else if ( registeredMeasures.containsKey( sortKeyBinding ) )
			{
				// Add sorting on measures
				Query targetQuery = i > 0 ? sd.getQuery( )
						: (Query) sd.getDesignTimeSeries( )
								.getDataDefinition( )
								.get( 0 );
				IMeasureDefinition mDef = registeredMeasures.get( sortKeyBinding );
				String targetBindingName = ChartXTabUtil.getBindingName( targetQuery.getDefinition( ),
						true );

				// Find measure binding
				Binding measureBinding = registeredBindings.get( sortKeyBinding );
				// Create new total binding on measure
				Binding aggBinding = new Binding( measureBinding.getBindingName( )
						+ targetBindingName );
				aggBinding.setDataType( measureBinding.getDataType( ) );
				aggBinding.setExpression( measureBinding.getExpression( ) );
				aggBinding.addAggregateOn( registeredQueries.get( targetBindingName ) );
				aggBinding.setAggrFunction( mDef.getAggrFunction( ) );
				cubeQuery.addBinding( aggBinding );

				ICubeSortDefinition sortDef = ChartXTabUtil.getCubeElementFactory( )
						.createCubeSortDefinition( ExpressionUtil.createJSDataExpression( aggBinding.getBindingName( ) ),
								registeredLevels.get( targetBindingName ),
								null,
								null,
								sd.getSorting( ) == SortOption.ASCENDING_LITERAL
										? ISortDefinition.SORT_ASC
										: ISortDefinition.SORT_DESC );
				cubeQuery.addSort( sortDef );
			}
		}
	}

	/**
	 * Adds measure or row/column edge according to query expression.
	 */
	private void bindSeriesQuery( String expr, ICubeQueryDefinition cubeQuery,
			CubeHandle cube ) throws BirtException
	{
		if ( expr != null && expr.length( ) > 0 )
		{
			String bindingName = ChartXTabUtil.getBindingName( expr, true );
			if ( bindingName != null && !ChartXTabUtil.isBinding( expr, false ) )
			{
				// Remove the operations from expression if it references
				// binding
				expr = ExpressionUtil.createJSDataExpression( bindingName );
			}

			Binding colBinding = null;
			if ( bindingName != null )
			{
				colBinding = registeredBindings.get( bindingName );
			}
			else
			{
				// We also support dimension/measure expressions as binding
				colBinding = registeredBindings.get( expr );
			}

			if ( colBinding != null || bindingName != null )
			{
				if ( colBinding == null )
				{
					// Get a unique name.
					bindingName = StructureFactory.newComputedColumn( handle,
							expr.replaceAll( "\"", "" ) ) //$NON-NLS-1$ //$NON-NLS-2$
							.getName( );
					colBinding = new Binding( bindingName );
					colBinding.setDataType( org.eclipse.birt.core.data.DataType.ANY_TYPE );
					colBinding.setExpression( new ScriptExpression( expr ) );

					registeredBindings.put( bindingName, colBinding );
					registeredQueries.put( bindingName, expr );

					// We also support dimension/measure expressions as binding
					registeredBindings.put( expr, colBinding );
				}
				else
				{
					bindingName = colBinding.getBindingName( );
					// Convert binding expression like data[] to raw expression
					// like dimension[] or measure[]
					expr = registeredQueries.get( bindingName );
				}

				// Add binding to query definition
				if ( !cubeQuery.getBindings( ).contains( colBinding ) )
				{
					cubeQuery.addBinding( colBinding );
				}

				if ( ChartXTabUtil.isBinding( expr, true ) )
				{
					bindSeriesQuery( ChartXTabUtil.getBindingName( expr, true ),
							cubeQuery,
							cube );
					return;
				}

				String measure = ChartXTabUtil.getMeasureName( expr );
				if ( measure != null )
				{
					if ( registeredMeasures.containsKey( bindingName ) )
					{
						return;
					}

					// Add measure
					IMeasureDefinition mDef = cubeQuery.createMeasure( measure );

					String aggFun = DataAdapterUtil.adaptModelAggregationType( cube.getMeasure( measure )
							.getFunction( ) );
					mDef.setAggrFunction( aggFun );
					registeredMeasures.put( bindingName, mDef );

					// AggregateOn has been added in binding when initializing
					// column bindings
				}
				else if ( ChartXTabUtil.isDimensionExpresion( expr ) )
				{
					if ( registeredLevels.containsKey( bindingName ) )
					{
						return;
					}

					// Add row/column edge
					String[] levels = ChartXTabUtil.getLevelNameFromDimensionExpression( expr );
					String dimensionName = levels[0];
					final int edgeType = getEdgeType( dimensionName );
					IEdgeDefinition edge = cubeQuery.getEdge( edgeType );
					IHierarchyDefinition hieDef = null;
					if ( edge == null )
					{
						// Only create one edge/dimension/hierarchy in one
						// direction
						edge = cubeQuery.createEdge( edgeType );
						IDimensionDefinition dimDef = edge.createDimension( dimensionName );
						hieDef = dimDef.createHierarchy( cube.getDimension( dimDef.getName( ) )
								.getDefaultHierarchy( )
								.getQualifiedName( ) );
					}
					else
					{
						hieDef = (IHierarchyDefinition) ( (IDimensionDefinition) edge.getDimensions( )
								.get( 0 ) ).getHierarchy( ).get( 0 );
					}

					// Create level
					boolean bMultipleLevels = !hieDef.getLevels( ).isEmpty( );
					ILevelDefinition levelDef = hieDef.createLevel( levels[1] );

					registeredLevels.put( bindingName, levelDef );

					LevelHandle levelHandle = handle.getModuleHandle( )
							.findLevel( levelDef.getHierarchy( )
									.getDimension( )
									.getName( )
									+ "/" + levelDef.getName( ) ); //$NON-NLS-1$

					registeredLevelHandles.put( levelHandle, levelDef );

					// Reset the level definitions by hierarchy order in
					// multiple levels case
					if ( bMultipleLevels )
					{
						Iterator levelsInOrder = getAllLevelsInHierarchyOrder( cube,
								cubeQuery ).iterator( );
						hieDef.getLevels( ).clear( );
						while ( levelsInOrder.hasNext( ) )
						{
							ILevelDefinition level = (ILevelDefinition) levelsInOrder.next( );
							hieDef.createLevel( level.getName( ) );
						}
					}
				}
			}
		}
	}

	private void addCubeFilter( ICubeQueryDefinition cubeQuery )
			throws BirtException
	{
		List levels = new ArrayList( );
		List values = new ArrayList( );

		Iterator filterItr = null;
		if ( handle.getContainer( ) instanceof MultiViewsHandle )
		{
			filterItr = getCrosstabFiltersIterator( );
		}
		else
		{
			filterItr = ChartReportItemUtil.getChartReportItemFromHandle( handle )
					.getCubeFiltersIterator( );
		}
		while ( filterItr.hasNext( ) )
		{
			FilterConditionElementHandle filterCon = (FilterConditionElementHandle) filterItr.next( );

			// clean up first
			levels.clear( );
			values.clear( );

			addMembers( levels, values, filterCon.getMember( ) );

			ILevelDefinition[] qualifyLevels = null;
			Object[] qualifyValues = null;

			if ( levels.size( ) > 0 )
			{
				qualifyLevels = (ILevelDefinition[]) levels.toArray( new ILevelDefinition[levels.size( )] );
				qualifyValues = values.toArray( new Object[values.size( )] );
			}

			ConditionalExpression filterCondExpr;

			if ( ModuleUtil.isListFilterValue( filterCon ) )
			{
				filterCondExpr = new ConditionalExpression( filterCon.getExpr( ),
						DataAdapterUtil.adaptModelFilterOperator( filterCon.getOperator( ) ),
						filterCon.getValue1List( ) );
			}
			else
			{
				filterCondExpr = new ConditionalExpression( filterCon.getExpr( ),
						DataAdapterUtil.adaptModelFilterOperator( filterCon.getOperator( ) ),
						filterCon.getValue1( ),
						filterCon.getValue2( ) );
			}

			ILevelDefinition levelDefinition = null;
			if ( filterCon.getMember( ) != null )
			{
				levelDefinition = registeredLevelHandles.get( filterCon.getMember( )
						.getLevel( ) );
			}
			else
			{
				levelDefinition = registeredLevels.get( ChartXTabUtil.getBindingName( filterCondExpr.getExpression( )
						.getText( ),
						true ) );
			}
			ICubeFilterDefinition filterDef = ChartXTabUtil.getCubeElementFactory( )
					.creatCubeFilterDefinition( filterCondExpr,
							levelDefinition,
							qualifyLevels,
							qualifyValues );

			cubeQuery.addFilter( filterDef );

		}
	}

	private Iterator getCrosstabFiltersIterator( )
	{
		DesignElementHandle handles = handle.getContainer( ).getContainer( );
		List list = new ArrayList( );
		if ( !( handles instanceof ExtendedItemHandle ) )
			return list.iterator( );
		CrosstabReportItemHandle crossTab = null;
		try
		{
			crossTab = (CrosstabReportItemHandle) ( (ExtendedItemHandle) handles ).getReportItem( );
		}
		catch ( ExtendedElementException e )
		{
			// TODO Auto-generated catch block
			logger.log( e );
		}
		if ( crossTab == null )
		{
			return list.iterator( );
		}
		if ( crossTab.getCrosstabView( ICrosstabConstants.COLUMN_AXIS_TYPE ) != null )
		{
			DesignElementHandle elementHandle = crossTab.getCrosstabView( ICrosstabConstants.COLUMN_AXIS_TYPE )
					.getModelHandle( );
			list.addAll( getLevelOnCrosstab( (ExtendedItemHandle) elementHandle ) );
		}

		if ( crossTab.getCrosstabView( ICrosstabConstants.ROW_AXIS_TYPE ) != null )
		{
			DesignElementHandle elementHandle = crossTab.getCrosstabView( ICrosstabConstants.ROW_AXIS_TYPE )
					.getModelHandle( );
			list.addAll( getLevelOnCrosstab( (ExtendedItemHandle) elementHandle ) );
		}

		int measureCount = crossTab.getMeasureCount( );
		for ( int i = 0; i < measureCount; i++ )
		{
			MeasureViewHandle measureView = crossTab.getMeasure( i );
			Iterator iter = measureView.filtersIterator( );
			while ( iter.hasNext( ) )
			{
				list.add( iter.next( ) );
			}
		}

		return list.iterator( );
	}

	private List getLevelOnCrosstab( ExtendedItemHandle handle )
	{
		CrosstabViewHandle crossTabViewHandle = null;
		try
		{
			crossTabViewHandle = (CrosstabViewHandle) handle.getReportItem( );
		}
		catch ( ExtendedElementException e )
		{
			// TODO Auto-generated catch block
			logger.log( e );
		}
		List list = new ArrayList( );
		if ( crossTabViewHandle == null )
		{
			return list;
		}
		int dimensionCount = crossTabViewHandle.getDimensionCount( );

		for ( int i = 0; i < dimensionCount; i++ )
		{
			DimensionViewHandle dimension = crossTabViewHandle.getDimension( i );
			int levelCount = dimension.getLevelCount( );
			for ( int j = 0; j < levelCount; j++ )
			{
				LevelViewHandle levelHandle = dimension.getLevel( j );
				Iterator iter = levelHandle.filtersIterator( );
				while ( iter.hasNext( ) )
				{
					list.add( iter.next( ) );
				}

			}
		}
		return list;
	}

	/**
	 * Recursively add all member values and associated levels to the given
	 * list.
	 */
	private void addMembers( List levels, List values, MemberValueHandle member )
	{
		if ( member != null )
		{
			Object levelDef = registeredLevelHandles.get( member.getLevel( ) );

			if ( levelDef != null )
			{
				levels.add( levelDef );
				values.add( member.getValue( ) );

				if ( member.getContentCount( IMemberValueModel.MEMBER_VALUES_PROP ) > 0 )
				{
					// only use first member here
					addMembers( levels,
							values,
							(MemberValueHandle) member.getContent( IMemberValueModel.MEMBER_VALUES_PROP,
									0 ) );
				}
			}
		}
	}

	/**
	 * Gets all levels and sorts them in hierarchy order in multiple levels
	 * case.
	 * 
	 * @param cubeHandle
	 * @param cubeQuery
	 */
	private Collection getAllLevelsInHierarchyOrder( CubeHandle cubeHandle,
			ICubeQueryDefinition cubeQuery )
	{
		Collection levelValues = registeredLevels.values( );
		// Only sort the level for multiple levels case
		if ( cubeQuery.getEdge( ICubeQueryDefinition.COLUMN_EDGE ) == null
				&& levelValues.size( ) > 1 )
		{
			List levelList = new ArrayList( levelValues.size( ) );
			String dimensionName = null;
			int firstLevelIndex = 0;
			int i = 0;
			HierarchyHandle hh = null;
			for ( Iterator iterator = levelValues.iterator( ); iterator.hasNext( ); i++ )
			{
				ILevelDefinition level = (ILevelDefinition) iterator.next( );

				if ( i == 0 )
				{
					dimensionName = level.getHierarchy( )
							.getDimension( )
							.getName( );
					hh = cubeHandle.getDimension( dimensionName )
							.getDefaultHierarchy( );
					while ( firstLevelIndex < hh.getLevelCount( ) )
					{
						if ( hh.getLevel( firstLevelIndex )
								.getName( )
								.equals( level.getName( ) ) )
						{
							break;
						}
						firstLevelIndex++;
					}

					levelList.add( level );
				}
				else
				{
					while ( firstLevelIndex < hh.getLevelCount( ) )
					{
						if ( hh.getLevel( firstLevelIndex )
								.getName( )
								.equals( level.getName( ) ) )
						{
							break;
						}
						firstLevelIndex++;
					}

					if ( firstLevelIndex < hh.getLevelCount( ) )
					{
						// Ascending order
						levelList.add( level );
					}
					else
					{
						// Descending order
						levelList.add( 0, level );
					}
				}
			}

			return levelList;
		}
		return levelValues;
	}

	private int getEdgeType( String dimensionName )
	{
		if ( this.rowEdgeDimension == null )
		{
			this.rowEdgeDimension = dimensionName;
			return ICubeQueryDefinition.ROW_EDGE;
		}
		return this.rowEdgeDimension.equals( dimensionName )
				? ICubeQueryDefinition.ROW_EDGE
				: ICubeQueryDefinition.COLUMN_EDGE;
	}

	static List getAllSeriesDefinitions( Chart chart )
	{
		List seriesList = new ArrayList( );
		if ( chart instanceof ChartWithAxes )
		{
			Axis xAxis = (Axis) ( (ChartWithAxes) chart ).getAxes( ).get( 0 );
			// Add base series definitions
			seriesList.addAll( xAxis.getSeriesDefinitions( ) );
			EList axisList = xAxis.getAssociatedAxes( );
			for ( int i = 0; i < axisList.size( ); i++ )
			{
				// Add value series definitions
				seriesList.addAll( ( (Axis) axisList.get( i ) ).getSeriesDefinitions( ) );
			}
		}
		else if ( chart instanceof ChartWithoutAxes )
		{
			SeriesDefinition sdBase = (SeriesDefinition) ( (ChartWithoutAxes) chart ).getSeriesDefinitions( )
					.get( 0 );
			seriesList.add( sdBase );
			seriesList.addAll( sdBase.getSeriesDefinitions( ) );
		}
		return seriesList;
	}

}
