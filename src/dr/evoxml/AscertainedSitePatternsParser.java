package dr.evoxml;

import dr.evolution.alignment.Alignment;
import dr.evolution.alignment.AscertainedSitePatterns;
import dr.evolution.alignment.PatternList;
import dr.evolution.util.TaxonList;
import dr.xml.*;

import java.util.logging.Logger;

/**
 * Package: AscertainedSitePatternsParser
 * Description:
 * <p/>
 * <p/>
 * Created by
 * Alexander V. Alekseyenko (alexander.alekseyenko@gmail.com)
 * Date: Mar 10, 2008
 * Time: 3:06:52 PM
 */
public class AscertainedSitePatternsParser extends AbstractXMLObjectParser {

    public static final String APATTERNS = "ascertainedPatterns";
    public static final String FROM = "from";
    public static final String TO = "to";
    public static final String EVERY = "every";
    public static final String TAXON_LIST = "taxonList";
    public static final String INCLUDE = "includePatterns";
    public static final String EXCLUDE = "excludePatterns";

    public String getParserName() { return APATTERNS; }

    public Object parseXMLObject(XMLObject xo) throws XMLParseException {
        Alignment alignment=(Alignment)xo.getChild(Alignment.class);
        XMLObject xoc=null;
        TaxonList taxa = null;

        int from = 0;
        int to = 0;
        int every = 1;
        int startInclude=-1;
        int stopInclude=-1;
        int startExclude=-1;
        int stopExclude=-1;


        if (xo.hasAttribute(FROM)) {
            from = xo.getIntegerAttribute(FROM) - 1;
            if (from < 0)
                throw new XMLParseException("illegal 'from' attribute in patterns element");
        }

        if (xo.hasAttribute(TO)) {
            to = xo.getIntegerAttribute(TO) - 1;
            if (to < 0 || to < from)
                throw new XMLParseException("illegal 'to' attribute in patterns element");
        }

        if (xo.hasAttribute(EVERY)) {
            every = xo.getIntegerAttribute(EVERY);
            if (every <= 0)
                throw new XMLParseException("illegal 'every' attribute in patterns element");
        }

        if (xo.hasSocket(TAXON_LIST)) {
            taxa = (TaxonList)xo.getElementFirstChild(TAXON_LIST);
        }

        if (from > alignment.getSiteCount())
            throw new XMLParseException("illegal 'from' attribute in patterns element");

        if (to > alignment.getSiteCount())
            throw new XMLParseException("illegal 'to' attribute in patterns element");

        int f = from + 1;
        int t = to;
        if (t == 0) t = alignment.getSiteCount();

	    if (xo.hasAttribute("id")) {
		    Logger.getLogger("dr.evoxml").info("Creating ascertained site patterns '" + xo.getId() + "' from positions "+
                            Integer.toString(f) + "-" + Integer.toString(t) +
                            " of alignment '" + alignment.getId() + "'");
            if (every > 1) {
			    Logger.getLogger("dr.evoxml").info("  only using every " + every + " site");
		    }
        }

        if(xo.hasSocket(INCLUDE)){
            xoc=(XMLObject)xo.getChild(INCLUDE);
            if(xoc.hasAttribute(FROM) && xoc.hasAttribute(TO)){
                startInclude=xoc.getIntegerAttribute(FROM)-1;
                stopInclude=xoc.getIntegerAttribute(TO)-1;
            }
            else{
                throw new XMLParseException("both from and to attributes are required for includePatterns");
            }

            if(startInclude<0 || stopInclude<startInclude){
                throw new XMLParseException("invalid 'from' and 'to' attributes in includePatterns");
            }
            Logger.getLogger("dr.evoxml").info("\tAscertainment: Patterns in columns "+(startInclude+1)+" to "+(stopInclude+1)+" are only possible. ");
        }

        if(xo.hasSocket(EXCLUDE)){
            xoc=(XMLObject)xo.getChild(EXCLUDE);
            if(xoc.hasAttribute(FROM) && xoc.hasAttribute(TO)){
                startExclude=xoc.getIntegerAttribute(FROM)-1;
                stopExclude=xoc.getIntegerAttribute(TO)-1;
            }
            else{
                throw new XMLParseException("both from and to attributes are required for excludePatterns");
            }

            if(startExclude<0 || stopExclude<startExclude){
                throw new XMLParseException("invalid 'from' and 'to' attributes in includePatterns");
            }
            Logger.getLogger("dr.evoxml").info("\tAscertainment: Patterns in columns "+(startExclude+1)+" to "+(stopExclude+1)+" are not possible. ");
        }

        AscertainedSitePatterns patterns = new AscertainedSitePatterns(alignment, taxa,
                                                                       from, to, every,
                                                                       startInclude,stopInclude,
                                                                       startExclude,stopExclude);

		Logger.getLogger("dr.evoxml").info("\tThere are " + patterns.getPatternCount() + " patterns in total.");


        return patterns;
    }

    public XMLSyntaxRule[] getSyntaxRules() { return rules; }

    private XMLSyntaxRule[] rules = new XMLSyntaxRule[] {
        AttributeRule.newIntegerRule(FROM, true, "The site position to start at, default is 1 (the first position)"),
        AttributeRule.newIntegerRule(TO, true, "The site position to finish at, must be greater than <b>" + FROM + "</b>, default is length of given alignment"),
        AttributeRule.newIntegerRule(EVERY, true, "Determines how many sites are selected. A value of 3 will select every third site starting from <b>" + FROM + "</b>, default is 1 (every site)"),
        new ElementRule(TAXON_LIST,
            new XMLSyntaxRule[] { new ElementRule(TaxonList.class) }, true),
        new ElementRule(Alignment.class),
        new ContentRule("<includePatterns from=\"Z\" to=\"X\"/>"),
        new ContentRule("<excludePatterns from=\"Z\" to=\"X\"/>")    
    };

    public String getParserDescription() {
        return "A weighted list of the unique site patterns (unique columns) in an alignment.";
    }

    public Class getReturnType() { return PatternList.class; }

}
