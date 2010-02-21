/*******************************************************************************
 * Copyright (c) 2004 Actuate Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *  Actuate Corporation  - initial API and implementation
 *******************************************************************************/

package org.eclipse.birt.report.model.parser;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.birt.report.model.api.util.StringUtil;
import org.eclipse.birt.report.model.core.DesignElement;
import org.eclipse.birt.report.model.util.AbstractParseState;
import org.eclipse.birt.report.model.util.AnyElementState;
import org.eclipse.birt.report.model.util.ElementStructureUtil;
import org.eclipse.birt.report.model.util.VersionUtil;
import org.eclipse.birt.report.model.util.XMLParserException;
import org.eclipse.birt.report.model.util.XMLParserHandler;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

/**
 * Parses overridden values in the element.
 * 
 */

class OverriddenValuesState extends AbstractParseState
{

	/**
	 * The handler to parse the file.
	 */

	private ModuleParserHandler handler;

	/**
	 * Link baseId to the virtual child element.
	 */

	private Map baseIdMap = new HashMap( );

	private ReportElementState parentState;

	/**
	 * Constructs <code>OverriddenValuesState</code> with the given handler and
	 * the root element.
	 * 
	 * @param handler
	 *            the handler to parse the file.
	 * @param element
	 *            the root element where overridden-values tags residents.
	 */

	OverriddenValuesState( ModuleParserHandler handler, DesignElement element,
			ReportElementState partentState )
	{

		this.handler = handler;
		this.parentState = partentState;

		assert element.canContainVirtualElements( );
		baseIdMap = ElementStructureUtil.getIdMap( handler.module, element );
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.birt.report.model.util.AbstractParseState#getHandler()
	 */

	public XMLParserHandler getHandler( )
	{
		return handler;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.eclipse.birt.report.model.util.AbstractParseState#startElement(java
	 * .lang.String)
	 */

	public AbstractParseState startElement( String tagName )
	{
		if ( DesignSchemaConstants.REF_ENTRY_TAG.equalsIgnoreCase( tagName ) )
			return new RefEntryState( handler );
		return super.startElement( tagName );
	}

	/**
	 * Parses overridden values for one extended element.
	 */

	class RefEntryState extends DesignParseState
	{

		/**
		 * The base id of the extended element.
		 */

		private long baseId = 0;

		/**
		 * The flag to indicate that whether the element with base id is
		 * existed.
		 */

		private boolean isBaseValid = true;

		/**
		 * Constrcuts <code>RefEntryState</code> with the given handler.
		 * 
		 * @param handler
		 *            the handler to parse the file
		 */

		RefEntryState( ModuleParserHandler handler )
		{
			super( handler );
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see
		 * org.eclipse.birt.report.model.util.AbstractParseState#getHandler()
		 */

		public XMLParserHandler getHandler( )
		{
			return handler;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see
		 * org.eclipse.birt.report.model.util.AbstractParseState#parseAttrs(
		 * org.xml.sax.Attributes)
		 */

		public void parseAttrs( Attributes attrs ) throws XMLParserException
		{
			String baseIdStr = attrs
					.getValue( DesignSchemaConstants.BASE_ID_ATTRIB );

			if ( baseIdStr == null )
			{
				// log this semantic warning.
				return;
			}

			try
			{
				baseId = Long.parseLong( baseIdStr );
			}
			catch ( NumberFormatException e )
			{
				DesignParserException ex = new DesignParserException(
						new String[]{baseIdStr},
						DesignParserException.DESIGN_EXCEPTION_INVALID_ELEMENT_ID );
				handler.getErrorHandler( ).semanticError( ex );
				isBaseValid = false;
				return;
			}

			// The element with the given base id is not found in the map(
			// baseId:virtualChild )

			boolean handleWithParentState = false;

			DesignElement virtualChild = getElement( );
			if ( virtualChild == null )
			{
				if ( OverriddenValuesState.this.parentState.getElement( )
						.getExtendsElement( ) == null )
				{
					handleWithParentState = true;
				}
				else
				{
					isBaseValid = false;

					DesignParserException ex = new DesignParserException(
							new String[]{baseIdStr},
							DesignParserException.DESIGN_EXCEPTION_VIRTUAL_PARENT_NOT_FOUND );
					handler.getErrorHandler( ).semanticWarning( ex );

					return;
				}
			}

			String name = attrs.getValue( DesignSchemaConstants.NAME_ATTRIB );
			if ( virtualChild != null && !StringUtil.isBlank( name ) )
			{
				virtualChild.setName( name );
			}

			long id = 0;
			// handle id
			try
			{
				String theID = attrs.getValue( DesignSchemaConstants.ID_ATTRIB );

				if ( !StringUtil.isBlank( theID ) )
				{
					// if the id is not null, parse it
					id = Long.parseLong( theID );

					if ( id <= 0 )
					{
						if ( virtualChild != null )
							handler
									.getErrorHandler( )
									.semanticError(
											new DesignParserException(
													new String[]{
															virtualChild
																	.getIdentifier( ),
															attrs
																	.getValue( DesignSchemaConstants.ID_ATTRIB )},
													DesignParserException.DESIGN_EXCEPTION_INVALID_ELEMENT_ID ) );
						return;
					}

					if ( handleWithParentState )
					{
						OverriddenValuesState.this.parentState
								.insertOverriddenRefValue(
										baseId,
										new ReportElementState.OverriddenRefValue(
												id, name ) );
						return;
					}

					DesignElement theElement = handler.module
							.getElementByID( id );

					if ( theElement != null
							&& handler.versionNumber >= VersionUtil.VERSION_3_2_7
							&& theElement != virtualChild )
						handler
								.getErrorHandler( )
								.semanticError(
										new DesignParserException(
												new String[]{
														theElement
																.getIdentifier( ),
														virtualChild
																.getIdentifier( )},
												DesignParserException.DESIGN_EXCEPTION_DUPLICATE_ELEMENT_ID ) );
					virtualChild.setID( id );
				}

			}
			catch ( NumberFormatException e )
			{
				if ( virtualChild != null )
					handler
							.getErrorHandler( )
							.semanticError(
									new DesignParserException(
											new String[]{
													virtualChild
															.getIdentifier( ),
													attrs
															.getValue( DesignSchemaConstants.ID_ATTRIB )},
											DesignParserException.DESIGN_EXCEPTION_INVALID_ELEMENT_ID ) );
			}
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see
		 * org.eclipse.birt.report.model.parser.DesignParseState#getElement()
		 */

		public DesignElement getElement( )
		{
			Object obj = baseIdMap.get( Long.valueOf( baseId ) );
			return (DesignElement) obj;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see
		 * org.eclipse.birt.report.model.util.AbstractParseState#startElement
		 * (java.lang.String)
		 */

		public AbstractParseState startElement( String tagName )
		{
			// if the base id is invalid, do not parse the child tag under the
			// <ref-entry>.

			if ( !isBaseValid )
				return new AnyElementState( getHandler( ) );

			if ( parentState.getElement( ).getDefn( ).canExtend( ) )
				return super.startElement( tagName );
			else
			{
				int tagValue = tagName.toLowerCase( ).hashCode( );
				if ( ParserSchemaConstants.PROPERTY_TAG == tagValue )
					return new PropertyNodeState( handler, baseId );
				return new AnyElementState( getHandler( ) );
			}
		}
	}

	class PropertyNodeState extends DesignParseState
	{

		private long id;
		private String propName = null;

		/**
		 * Constrcuts <code>PropertyNodeState</code> with the given handler.
		 * 
		 * @param handler
		 *            the handler to parse the file
		 */

		PropertyNodeState( ModuleParserHandler handler, long id )
		{
			super( handler );
			this.id = id;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see
		 * org.eclipse.birt.report.model.parser.DesignParseState#getElement()
		 */
		public DesignElement getElement( )
		{
			return null;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see org.eclipse.birt.report.model.util.AbstractParseState#end()
		 */
		public void end( ) throws SAXException
		{
			String value = text.toString( );
			if ( parentState != null )
			{
				parentState.insertOverridenPropertyValue( id, propName, value );
			}
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see
		 * org.eclipse.birt.report.model.util.AbstractParseState#parseAttrs(
		 * org.xml.sax.Attributes)
		 */
		public void parseAttrs( Attributes attrs ) throws XMLParserException
		{
			propName = attrs.getValue( DesignSchemaConstants.NAME_ATTRIB );
		}

	}
}
